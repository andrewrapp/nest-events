package modules

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides}
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.service.ResponseClassifier.{RetryOnChannelClosed, RetryOnThrows, RetryOnTimeout, RetryOnWriteExceptions}
import com.twitter.finagle.service.{Backoff, ReqRep, ResponseClass, RetryBudget}
import com.twitter.finagle.{Http, Service}
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.util.Return
import com.google.inject.{Module, Provides, Singleton}
import com.twitter.inject.Logging
import net.codingwell.scalaguice.ScalaModule

import java.net.URL

class SystemEnvOauthConfigModule extends AbstractModule with ScalaModule with Logging {
  @Singleton
  @Provides
  def providesSystemEnv(): SystemEnv = {
    info(s"Cloud function env ${System.getenv()}")
    SystemEnv(System.getenv())
  }
}