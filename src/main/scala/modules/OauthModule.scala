package modules

import com.google.inject.{AbstractModule, Provides}
import oauth.{GoogleOauthCredentials, GoogleRefreshToken}
import com.google.inject.{Module, Provides, Singleton}
import net.codingwell.scalaguice.ScalaModule

import java.util

class OauthModule extends AbstractModule with ScalaModule {
  @Singleton
  @Provides
  def providesOauth(systemEnv: SystemEnv): GoogleOauthCredentials = {
    (for {
      clientId <- Option(systemEnv.env.get("clientId"))
      clientSecret <- Option(systemEnv.env.get("clientSecret"))
    } yield GoogleOauthCredentials(clientId, clientSecret)
      )
      .getOrElse(throw new Exception("Oauth creds not supplied as cloud function env parameters"))
  }

  @Singleton
  @Provides
  def providesRefreshToken(systemEnv: SystemEnv): GoogleRefreshToken = {
      Option(GoogleRefreshToken(systemEnv.env.get("refreshToken"))).getOrElse(throw new Exception("refreshToken not supplied as cloud function env parameter"))
  }
}