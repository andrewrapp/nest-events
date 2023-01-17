package functions

import com.google.cloud.MetadataConfig.getProjectId
import com.google.cloud.bigquery.{BigQueryOptions, QueryJobConfiguration, QueryParameterValue}
import com.google.cloud.functions.{HttpFunction, HttpRequest, HttpResponse}
import com.google.inject.name.Names
import com.google.inject.{Guice, Key}
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.util._
import domain.{PubsubMessage, SdmEvent, SdmTraits}
import modules.{FinagleClientsModule, OauthModule, SerializationModule, SystemEnvOauthConfigModule}
import net.codingwell.scalaguice.InjectorExtensions._
import services.SdmService

import java.io.PrintWriter
import java.util.logging.{Level, Logger}
import java.util.stream.Collectors
import scala.util.control.NonFatal

class NestEventsPubSubReceiver extends HttpFunction {

    val bigquery = BigQueryOptions.getDefaultInstance().getService()
    val logger = Logger.getLogger(this.getClass.getName)

    val injector = Guice.createInjector(new FinagleClientsModule, new OauthModule, new SerializationModule(), new SystemEnvOauthConfigModule())

    val camelCaseMapper: ScalaObjectMapper = injector.getInstance(Key.get(classOf[ScalaObjectMapper], Names.named("CamelCaseObjectMapper")))

    val sdmService = injector.instance[SdmService]

    override def service(request: HttpRequest, response: HttpResponse): Unit = {
        val body: String = request.getReader.lines().collect(Collectors.joining());

        logger.info("Received pubsub message " + body.toString())

        try {
            val pubsubMessage: PubsubMessage = camelCaseMapper.parse[PubsubMessage](body)
            val sdmEvent = camelCaseMapper.parse[SdmEvent](pubsubMessage.message.getDataJson())

            logger.info("SDM message " + pubsubMessage.message.getDataJson())

            Try(insertEvent(pubsubMessage, sdmEvent))
              .onFailure {
                // throw to signal pubsub retry
                  case NonFatal(e) =>
                    throw new Exception("Failed to insert event into big query from event " + pubsubMessage.message.getDataJson(), e)
            }

            Try(sdmService.processImageEvent(sdmEvent)).onFailure{
                case NonFatal(e) => logger.log(Level.SEVERE, s"Failed to process image event", e)
            }

            sdmEvent.getHvacStatus().foreach{ status =>
                Try(insertHvacEvent(sdmEvent, status)).onFailure{
                    case NonFatal(e) => logger.log(Level.SEVERE, s"Failed to insert hvac event", e)
                }
            }

            // ack
            response.setStatusCode(200);
        } catch {
            case NonFatal(e) =>
                logger.log(Level.SEVERE, "Request failed", e)
                // nack
                response.setStatusCode(500)
                val writer = new PrintWriter(response.getWriter())
                writer.printf("Failed %s", e.toString())
        }
    }

    def insertEvent(pubsubMessage: PubsubMessage, sdmEvent: SdmEvent) = {
        val queryConfig = QueryJobConfiguration.newBuilder(
            s"""insert into `${getProjectId}.nest_events.events_v2`(event_id, message_id, event_timestamp, publish_timestamp, ingest_timestamp, json_text, resources)
              |values(@eventId, @messageId, TIMESTAMP(@eventTimestamp), TIMESTAMP(@publishTimestamp), CURRENT_TIMESTAMP(), @jsonText, @resources)""".stripMargin
            )
            .setUseLegacySql(false)
            .addNamedParameter("eventId", QueryParameterValue.string(sdmEvent.eventId))
            .addNamedParameter("messageId", QueryParameterValue.string(pubsubMessage.message.messageId))
            .addNamedParameter("eventTimestamp", QueryParameterValue.string(sdmEvent.timestamp))
            .addNamedParameter("publishTimestamp", QueryParameterValue.string(pubsubMessage.message.publishTime))
            .addNamedParameter("jsonText", QueryParameterValue.string(pubsubMessage.message.getDataJson()))
            .addNamedParameter("resources", QueryParameterValue.string(sdmEvent.resourceUpdate.getResourcesCsv()))
            .build()

        logger.info("Inserting into bigquery " + queryConfig.getQuery());

        val tableResult = bigquery.query(queryConfig);

        if (tableResult.getTotalRows != 1) {
            throw new Exception(s"Failed to insert sdm event ${sdmEvent} with pubsub event id ${pubsubMessage.message.messageId} into bigquery. Expected 1 table row updated result but got ${tableResult.getTotalRows}")
        }
    }

    def insertHvacEvent(sdmEvent: SdmEvent, status: String) = {
        val queryConfig = QueryJobConfiguration.newBuilder(
            s"""insert into `${getProjectId}.nest_events.hvac_events`(event_id, trait, status, recorded_at, event_timestamp)
               |values(@eventId, @trait, @status, CURRENT_TIMESTAMP(), TIMESTAMP(@eventTimestamp))""".stripMargin
        )
          .setUseLegacySql(false)
          .addNamedParameter("eventId", QueryParameterValue.string(sdmEvent.eventId))
          .addNamedParameter("trait", QueryParameterValue.string(SdmTraits.HVAC))
          .addNamedParameter("status", QueryParameterValue.string(status))
          .addNamedParameter("eventTimestamp", QueryParameterValue.string(sdmEvent.timestamp))
          .build()

        logger.info("Inserting into hvac event into bigquery " + queryConfig.getQuery());

        val tableResult = bigquery.query(queryConfig);

        if (tableResult.getTotalRows != 1) {
            throw new Exception(s"Failed to insert hvac event ${sdmEvent} with event id ${sdmEvent.eventId} into bigquery")
        }
    }
}