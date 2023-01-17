package exceptions

import domain.GoogleApiErrorResponse

case class GoogleApiException(message: String, googleApiErrorResponse: GoogleApiErrorResponse) extends Exception(s"${message}: ${googleApiErrorResponse}")
