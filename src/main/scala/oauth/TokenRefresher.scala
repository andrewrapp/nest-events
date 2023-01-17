package oauth

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.google.inject.name.Named
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, RequestBuilder, Response, Status}
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.inject.Logging
import com.twitter.util
import com.twitter.util.{Duration, Future, Stopwatch, Timer}
import domain.{GoogleApiError, GoogleApiErrorResponse}
import exceptions.GoogleApiException

import java.net.URLEncoder
import java.util.concurrent.TimeUnit


object TokenRefresher {
  // has nothing to do with this class
  def authorizationHeader(accessToken: String): (String, String) = {
    ("Authorization", s"Bearer $accessToken")
  }
}

class TokenRefresher @Inject() (
                                 oauthCredentials: GoogleOauthCredentials,
                                 googleBearerToken: GoogleRefreshToken,
                                 @Named("GoogleOauthHttpClient") httpClient: Service[Request, Response],
                                 @Named("SnakeCaseObjectMapper") snakeCaseObjectMapper: ScalaObjectMapper
                               ) extends Logging {

  def encode(param: String) = URLEncoder.encode(param, "UTF-8")

  private def parseAuthResponse(response: String): OauthToken = {
    val json = new ObjectMapper().readTree(response)

    snakeCaseObjectMapper.parse[OauthToken](response)
  }

  def requestNewToken(): Future[OauthToken] = {
    info("Request token from refresh token")

    val request = RequestBuilder()
      .url("https://www.googleapis.com/oauth2/v4/token")
      .addFormElement(
        ("client_id" -> oauthCredentials.clientId),
        ("client_secret" -> oauthCredentials.secret),
        ("refresh_token" -> googleBearerToken.refreshToken),
        ("grant_type" -> "refresh_token")
      )
      .buildFormPost()

    val timer: util.Stopwatch.Elapsed = Stopwatch.start()

    httpClient(request).map { response =>
      if (response.status == Status.Ok) {
        info(s"Token refresh returned ${response.contentString} in ${timer().inUnit(TimeUnit.MILLISECONDS)}ms")
        parseAuthResponse(response.contentString)
      } else {
        // messages are neither camel nor snake case so will work with either
        val error = snakeCaseObjectMapper.parse[GoogleApiErrorResponse](response.contentString)
        throw GoogleApiException(s"Token request in auth flow was not successful. headers ${response.headerMap}", error)
      }
    }
  }
}
