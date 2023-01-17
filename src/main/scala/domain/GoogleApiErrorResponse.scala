package domain

// ex:
//          "error": {
//            "code": 503,
//            "message": "The camera image is no longer available for download.",
//            "status": "UNAVAILABLE"
//          }
case class GoogleApiError(code: Int, message: String, status: String)
case class GoogleApiErrorResponse(error: GoogleApiError)
