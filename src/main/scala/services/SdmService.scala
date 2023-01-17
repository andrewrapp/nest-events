package services

import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions}
import com.google.inject.Inject
import com.google.inject.name.Named
import com.twitter
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.inject.Logging
import com.twitter.io.Buf
import com.twitter.util
import com.twitter.util.{Await, Future, Stopwatch, Try}
import domain._
import modules.SdmClientBuilder
import oauth.{OauthToken, TokenRefresher}

import java.net.URL
import java.util.concurrent.TimeUnit

class SdmService @Inject()(
                            @Named("GoogleSdmHttpClient") sdmClient: Service[Request, Response],
                            sdmClientBuilder: SdmClientBuilder,
                            @Named("CamelCaseObjectMapper") camelCaseObjectMapper: ScalaObjectMapper,
                            tokenRefresher: TokenRefresher
                          ) extends Logging {

  private def generateImage(sdmEvent: SdmEvent, oauthToken: OauthToken): Future[GenerateImageResponse] = {

    if (sdmEvent.resourceUpdate.events.get.keySet.size > 1) {
      logger.warn(s"I'm not prepared to handle multiple events ${sdmEvent}")
    }

    val eventType = sdmEvent.resourceUpdate.events.get.keySet.head

    val json =
      s"""
        |{
        |    "command" : "sdm.devices.commands.CameraEventImage.GenerateImage",
        |    "params" : {
        |      "eventId": "${sdmEvent.resourceUpdate.getDeviceEventId(eventType)}"
        |    }
        |}
        |""".stripMargin

    // project id is the sdm project-id, not gcp
    // "/enterprises/" + project-id + "/devices/" + sdmEvent.resourceUpdate.getDeviceId + ":executeCommand"
    // resourceUpdate.name: enterprises/0f9309d0-c49f-4bc7-ad34-f95106965b2b/devices/AVPHwEs3PW_ufEm1HEuf-ae8nvaEtQcKxEz6Bu49Djd2I9XULjHp49rMUakllK-hX496hB1wUHwNnZw6AVZBtZRjheSTtA
    val command = sdmEvent.resourceUpdate.name + ":executeCommand"

    // Note: Event images expire 30 seconds after the event is published
    val url = "https://smartdevicemanagement.googleapis.com/v1/" + command
    val request = Request(Version.Http11, Method.Post, url)
    request.headerMap.add("Authorization", s"Bearer ${oauthToken.accessToken}")
    request.setContentType("application/json")
    request.setContentString(json)

    info(s"Generating image for event with url ${url}, auth ${oauthToken.accessToken}, and json payload ${json}, for event ${sdmEvent}")

    sdmClient(request).map{ response =>
      if (response.status == Status.Ok) {
        camelCaseObjectMapper.parse[GenerateImageResponse](response.contentString)
      } else {
        // rate limit super stingy at 5 requests per minute. https://developers.google.com/nest/device-access/project/limits#sandbox_rate_limits
        // retrying won't be of much use since they expire in 30s. Could keep track of windows and prioritize person events
        // GoogleApiError(429,Rate limited for the GenerateCameraEventImage command for the user.,RESOURCE_EXHAUSTED))
        val error = camelCaseObjectMapper.parse[GoogleApiErrorResponse](response.contentString)
        throw new Exception(s"GenerateImage failed ${error}, headers ${response.headerMap.toMap}")
      }
    }
  }

  def processImageEvent(sdmEvent: SdmEvent): Option[Unit] = {
    val bytes: Option[Array[Byte]] = Await.result(if (sdmEvent.isImageEvent()) {
      downloadEventImage(sdmEvent)
    } else {
      info(s"Ignoring image event ${sdmEvent}")
      Future.None
    })

    bytes.map{ bytes =>
      logger.info(s"Successful result for image download of size ${bytes.size}")
      uploadImageToGcs(sdmEvent.eventId + "-" + sdmEvent.resourceUpdate.getEventsResources().headOption.getOrElse("missing-resource") +".jpg", bytes)
    }
  }

  private def listDevices(oauthToken: OauthToken): Future[ListStructures] = {

    val url = s"https://smartdevicemanagement.googleapis.com/v1/enterprises/${SdmProjectId}/devices"

    val request = Request(Version.Http11, Method.Get, url)
    request.headerMap.add("Authorization", s"Bearer ${oauthToken.accessToken}")

    info(s"Requesting list devices ${url}")

    sdmClient(request).map{ response =>
      if (response.status == Status.Ok) {
        com.twitter.util.Try(camelCaseObjectMapper.parse[ListStructures](response.contentString))
          .onSuccess(s => info(s"Parsed ${response.contentString} into ${s}"))
          .onFailure(t => error(s"Failed to deserialize ${response.contentString}", t)).get()
      } else {
        val error = camelCaseObjectMapper.parse[GoogleApiErrorResponse](response.contentString)
        throw new Exception(s"List structures failed ${error}, headers ${response.headerMap.toMap}")
      }
    }
  }

  private def getThermostatStats(oauthToken: OauthToken): Future[ThermostatStats] = {
    listDevices(oauthToken).map(_.getThermoStats())
      .onSuccess(stats => info(s"Received thermostat stats ${stats}"))
  }

  val SdmProjectId = ""

  def getThermostatStats(): Future[ThermostatStats] = {
    for {
      token <- tokenRefresher.requestNewToken()
      stats <- getThermostatStats(token)
    } yield(stats)
  }

  private def downloadImage(generateImageResponse: GenerateImageResponse, width: Int = 1920 /* nest hello supports 1920x1080 */): Future[Array[Byte]] = {
    // finagle is not designed for arbitrary http gets since the client construction relies on knowing the host/method

      val url = new URL(s"${generateImageResponse.results.url}?width=${width}")

//    curl -H "Authorization: Basic g.0.eventToken" \
//      https://domain/sdm_event_snapshot/dGNUlTU2CjY5Y3VKaTZwR3o4Y1...

    val client: Service[Request, Response] = sdmClientBuilder.build(new URL(generateImageResponse.results.url))

    info(s"Downloading image from ${url}, with token ${generateImageResponse.results.token}")

    val request = Request(Version.Http11, Method.Get, s"${generateImageResponse.results.url}?width=${width}")
    request.headerMap.add("Authorization", s"Basic ${generateImageResponse.results.token}")

    val timer: util.Stopwatch.Elapsed = Stopwatch.start()

    client(request).map{ response =>
      if (response.status == Status.Ok) {
        val bytes = Buf.ByteArray.Owned.extract(response.content)
        info(s"Downloaded image in ${timer().inUnit(TimeUnit.MILLISECONDS)}ms")
        bytes
      } else {
        val error: Try[GoogleApiErrorResponse] = Try(camelCaseObjectMapper.parse[GoogleApiErrorResponse](response.contentString)).onFailure(t => warn(s"Failed to parse google api error ${response.contentString}"))
        throw new Exception(s"Download image failed ${error}, headers ${response.headerMap.toMap}")
      }
    }
  }

  def downloadEventImage(sdmEvent: SdmEvent): Future[Option[Array[Byte]]] =  {
    if (sdmEvent.isImageEvent()) {
      for {
        token <- tokenRefresher.requestNewToken()
        generatedImage <- generateImage(sdmEvent, token)
        imageBytes: Array[Byte] <- downloadImage(generatedImage)
      } yield (Some(imageBytes))
    } else {
      Future.None
    }
  }

  import com.google.cloud.MetadataConfig.getProjectId

  def uploadImageToGcs(objectName: String, bytes: Array[Byte]): Unit = {
    val storage: Storage = StorageOptions.newBuilder.setProjectId(getProjectId).build.getService

    val blobId = BlobId.of("nest-images", objectName)
    val blobInfo = BlobInfo.newBuilder(blobId).build

    val timer: twitter.util.Stopwatch.Elapsed = Stopwatch.start()

    info(s"Uploading image ${objectName}")

    val blob = storage.create(blobInfo, bytes)

    info(s"Uploaded image of size ${blob.getSize} in ${timer().inUnit(TimeUnit.MILLISECONDS)}ms")
  }
}
