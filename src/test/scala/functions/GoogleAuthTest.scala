package functions

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Guice, Provides, Singleton}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Future}
import modules.{OauthModule, SerializationModule, SystemEnv}
import net.codingwell.scalaguice.InjectorExtensions._
import oauth.{OauthToken, TokenRefresher}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.funsuite.AnyFunSuite

import java.util

class GoogleAuthTest extends AnyFunSuite {

  val client: Service[Request, Response] = mock[Service[Request, Response]]

  val injector = Guice.createInjector(new OauthModule(), new SerializationModule(), new AbstractModule() {
    @Singleton
    @Provides
    @Named("GoogleOauthHttpClient")
    def providesOauthHttpClient(): Service[Request, Response] = {
      client
    }

    @Provides
    def providesSystemEnv(): SystemEnv = {
      val map = new util.HashMap[String, String]()
      map.put("clientId", "id")
      map.put("clientSecret", "secret")
      map.put("refreshToken", "token")

      SystemEnv(map)
    }
  })

  test("token test") {
    val response = Response(Status.Ok)

    response.setContentString(
      """{"token_type":"bearer","access_token":"AAAAAAAAAAAAAAAAAAAAAB2WBQAAAAAAhAIHU2PPTWYE5jsyY0ML9x36JRw%3DZNBT5O9oyQkthKSwAaGpaYWkpM06u1J0KSlvIStyC6M2dsXhCb", "expires_in": 3600, "scope": "?"}""".stripMargin)

    // use arg captor
    when(client.apply(any[Request])).thenReturn(Future(response))

    val tokenRefresher = injector.instance[TokenRefresher]

    val token: OauthToken = Await.result(tokenRefresher.requestNewToken())

    assert(token.accessToken == "AAAAAAAAAAAAAAAAAAAAAB2WBQAAAAAAhAIHU2PPQWYE5jsyX0ML9x36JRw%3DZNBT5O9oyQkthKSwAaGpaYWkpM06u1J0KSlvIStyC6M2dsXhCb")
  }
}