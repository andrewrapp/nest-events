package modules

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.service.ResponseClassifier.{RetryOnChannelClosed, RetryOnThrows, RetryOnTimeout, RetryOnWriteExceptions}
import com.twitter.finagle.service.{Backoff, ReqRep, ResponseClass, RetryBudget}
import com.twitter.finagle.{Http, Service}
import com.twitter.inject.Logging
import com.twitter.util.Return
import net.codingwell.scalaguice.ScalaModule

import java.net.URL

class FinagleClientsModule extends AbstractModule with ScalaModule with Logging {

  val retryOn500: PartialFunction[com.twitter.finagle.service.ReqRep, com.twitter.finagle.service.ResponseClass] = {
    case ReqRep(_, Return(resp: Response)) if resp.statusCode >= 400 && resp.statusCode < 500 =>
      // log
      ResponseClass.NonRetryableFailure
    case ReqRep(_, Return(resp: Response)) if resp.statusCode >= 500 =>
      // log
      ResponseClass.RetryableFailure
  }

  @Singleton
  @Provides
  @Named("GoogleOauthHttpClient")
  def providesOauthHttpClient(): Service[Request, Response] = {
    Http.client
      .withTls("www.googleapis.com")
      .withLabel("example_client")
      .withSessionQualifier.noFailFast
      .withSessionQualifier.noFailureAccrual
      .withRetryBackoff(Backoff.const(500.millis))
      .methodBuilder("inet!www.googleapis.com:443")
      .withTimeoutTotal(12.seconds)
      .withMaxRetries(3)
      .withTimeoutPerRequest(5.seconds)
      .withRetryForClassifier(retryOn500.orElse(RetryOnThrows).orElse(RetryOnTimeout).orElse(RetryOnChannelClosed).orElse(RetryOnWriteExceptions))
      .idempotent(0.0)
      .newService(methodName = "oauth_refresh")
  }

  @Singleton
  @Provides
  @Named("GoogleSdmHttpClient")
  def providesSdmHttpClient(): Service[Request, Response] = {
    Http.client
      .withTls("smartdevicemanagement.googleapis.com")
      .withLabel("example_client")
      .withSessionQualifier.noFailFast
      .withSessionQualifier.noFailureAccrual
      .withRetryBackoff(Backoff.const(500.millis))
      .methodBuilder("inet!smartdevicemanagement.googleapis.com:443")
      .withTimeoutTotal(12.seconds)
      .withMaxRetries(3)
      .withTimeoutPerRequest(5.seconds)
      .withRetryForClassifier(retryOn500.orElse(RetryOnThrows).orElse(RetryOnTimeout).orElse(RetryOnChannelClosed).orElse(RetryOnWriteExceptions))
      .idempotent(0.0)
      .newService(methodName = "oauth_refresh")
  }

  private def downloadClient(url: URL): Service[Request, Response] = {

    val port = if (url.getPort == -1) {
      if (url.getProtocol == "https") {
        info(s"Port is missing from url ${url}. Defaulting to 443")
        443
      } else {
        info(s"Port is missing from url ${url}. Defaulting to 80")
        80
      }
    }

    Http.client
      .withTls(url.getHost)
      .withRequestTimeout(20.seconds)
      .withResponseClassifier(retryOn500.orElse(RetryOnThrows).orElse(RetryOnTimeout).orElse(RetryOnChannelClosed).orElse(RetryOnWriteExceptions))
      .withRetryBudget(RetryBudget())
      .withRetryBackoff(Backoff.const(200.millis))
      .withSessionQualifier.noFailFast
      .withHttp2
      .newService(s"${url.getHost}:${port}")
  }

  @Singleton
  @Provides
  def providesImageDownloadClient(): SdmClientBuilder = {
    SdmClientBuilder(url => downloadClient(url))
  }
}