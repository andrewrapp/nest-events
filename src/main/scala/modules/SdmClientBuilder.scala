package modules

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}

import java.net.URL

case class SdmClientBuilder(build: URL => Service[Request, Response])