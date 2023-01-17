package functions

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Guice, Injector, Provides, Singleton}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.{Await, Future}
import modules.{SystemEnv, OauthModule, SdmClientBuilder, SerializationModule}
import net.codingwell.scalaguice.InjectorExtensions._
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.funsuite.AnyFunSuite
import services.SdmService

import java.util

class SdmServiceTest extends AnyFunSuite {

  val sdmClient: Service[Request, Response] = mock[Service[Request, Response]]
  val downloadImageClient: Service[Request, Response] = mock[Service[Request, Response]]
  val oauthClient: Service[Request, Response] = mock[Service[Request, Response]]

  val injector: Injector = Guice.createInjector(new OauthModule(), new SerializationModule(), new AbstractModule() {
    @Singleton
    @Provides
    @Named("GoogleSdmHttpClient")
    def providesSdmHttpClient(): Service[Request, Response] = {
      sdmClient
    }

    @Singleton
    @Provides
    @Named("GoogleOauthHttpClient")
    def providesOauthHttpClient(): Service[Request, Response] = {
      oauthClient
    }

    @Singleton
    @Provides
    def providesSystemEnv(): SystemEnv = {
      val map = new util.HashMap[String, String]()
      map.put("clientId", "id")
      map.put("clientSecret", "secret")
      map.put("refreshToken", "token")

      SystemEnv(map)
    }

    @Singleton
    @Provides
    def providesImageDownloadClient(): SdmClientBuilder = {
      SdmClientBuilder(url =>
        downloadImageClient
      )
    }
  })

  test("download event") {
    val sdmService = injector.instance[SdmService]

    val oauthResponse = Response(Status.Ok)
    oauthResponse.setContentString(
      """{"token_type":"bearer","access_token":"AAAAAAAAAAAAAAAAAAAAAB2WBTAAAAAAhAIHU2PPQWYE5jsyY0ML9x36JRw%3DZNBT5O9oyQkthKSwAaGpaYWkpM06u1J0KSlvIStyC6M2dsXhCb", "expires_in": 3600, "scope": "?"}""".stripMargin)

    when(oauthClient.apply(any[Request])).thenReturn(Future(oauthResponse))

    val generateImageResponse = Response(Status.Ok)

    generateImageResponse.setContentString(
      """
        |            {
        |              "results" : {
        |                "url" : "https://domain/sdm_event_snapshot/dGNUlTU2CjY5Y3VKaTZwR3o4Y1...",
        |                "token" : "g.0.eventToken"
        |              }
        |            }
        |""".stripMargin)

    when(sdmClient.apply(any[Request])).thenReturn(Future(generateImageResponse))

    val response = Response(Status.Ok)
    response.content(Buf.ByteArray.apply("not an image".getBytes : _*))

    when(downloadImageClient.apply(any[Request])).thenReturn(Future(response))

    val image: Option[Array[Byte]] = Await.result(sdmService.downloadEventImage(TestEvents.personEvent))

    assert(new String(image.get) == "not an image")
  }
}