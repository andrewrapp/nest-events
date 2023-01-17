package functions

import com.google.cloud.MetadataConfig.getProjectId
import com.google.cloud.bigquery._
import com.google.cloud.functions.{HttpFunction, HttpRequest, HttpResponse}
import com.google.inject.Guice
import com.twitter.util.{Await, Try}
import domain.ThermostatStats
import modules.{FinagleClientsModule, OauthModule, SerializationModule, SystemEnvOauthConfigModule}
import services.SdmService

import java.io.PrintWriter
import java.lang
import java.util.logging.{Level, Logger}
import scala.util.control.NonFatal

class NestThermostatLogger extends HttpFunction {
  val bigquery = BigQueryOptions.getDefaultInstance().getService()
  val logger = Logger.getLogger(this.getClass.getName)

  val injector = Guice.createInjector(new FinagleClientsModule, new OauthModule, new SerializationModule(), new SystemEnvOauthConfigModule())
  val sdmService = injector.getInstance(classOf[SdmService])

  override def service(request: HttpRequest, response: HttpResponse): Unit = {
    logger.info(s"Thermostat log received request")

    Try {
      val stats: ThermostatStats = Await.result(sdmService.getThermostatStats())
      bigqueryInsert(stats)
      writeResponse(response, 200)
    }.onFailure {
      case NonFatal(e) =>
        logger.log(Level.SEVERE, "Failed to process thermostats stat log request", e)
        // signal 500 for retry
        writeResponse(response, 500)
    }
  }

  private def writeResponse(response: HttpResponse, status: Int) = {
    val writer = new PrintWriter(response.getWriter())
    response.setStatusCode(status);
  }

  private def bigqueryInsert(thermostatStats: ThermostatStats): Unit = {
    val queryConfig = QueryJobConfiguration.newBuilder(
      s"""insert into `${getProjectId}.nest_events.thermostat_stats`(mode, relative_humidity, temperature_fahrenheit, heat_setpoint_fahrenheit, cool_setpoint_fahrenheit, recorded_at)
        |values(@mode, @relativeHumidity, @temperatureFahrenheit, @heatSetPointFahrenheit, @coolSetPointFahrenheit, CURRENT_TIMESTAMP())""".stripMargin
    )
      .setUseLegacySql(false)
      .addNamedParameter("mode", QueryParameterValue.string(thermostatStats.mode.getOrElse("FIXME")))
      .addNamedParameter("relativeHumidity", QueryParameterValue.int64(thermostatStats.relativeHumidity.map(lang.Integer.valueOf(_)).orNull))
      .addNamedParameter("temperatureFahrenheit", QueryParameterValue.float64(thermostatStats.temperatureFahrenheit.map(lang.Double.valueOf(_)).orNull))
      .addNamedParameter("heatSetPointFahrenheit", QueryParameterValue.float64(thermostatStats.heatSetPointFahrenheit.map(lang.Double.valueOf(_)).orNull))
      .addNamedParameter("coolSetPointFahrenheit", QueryParameterValue.float64(thermostatStats.coolSetPointFahrenheit.map(lang.Double.valueOf(_)).orNull))
      .build()

    logger.info("Inserting into thermostat stats into bigquery " + queryConfig.getQuery());

    val tableResult = bigquery.query(queryConfig);

    if (tableResult.getTotalRows != 1) {
      throw new Exception("Failed to insert thermostats stats record")
    }
  }
}
