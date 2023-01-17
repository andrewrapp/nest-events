package functions;

import org.junit.Assert.assertEquals
import org.scalatest.funsuite.AnyFunSuite

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse};

class PubSubTest extends AnyFunSuite {
  val client = HttpClient.newHttpClient();

  // first start server:  mvn function:run
  ignore("helloHttp_shouldRunWithFunctionsFramework") {
    val functionUrl = "http://localhost:8080/HelloHttp"

    // [START functions_http_system_test]
    val request = HttpRequest.newBuilder()
            .uri(URI.create(functionUrl))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    val response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(response.body().toString(), "Hello world!")
  }
}