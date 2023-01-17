package domain

case class GenerateImageResponseResults(url: String, token: String)
case class GenerateImageResponse(results: GenerateImageResponseResults)
