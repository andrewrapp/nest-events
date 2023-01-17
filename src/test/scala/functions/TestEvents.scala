package functions

import domain.{ResourceUpdate, SdmEvent}
import scala.jdk.CollectionConverters._

object TestEvents {
  val humidityEvent = SdmEvent("74c53c17-68f4-4ded-87c2-cb522ffa8394",
    "2021-04-23T14:32:46.844606Z",
    ResourceUpdate(
      name = "enterprises/0f9309d0-c49f-4bc7-ad34-f95106965b2b/devices/AVPHwEvo5ks2yhYAo9D8a0V-BVhI2qrOsx35LlCyxnsIjayu7JCKmuun-lq-W1VUshI7b_STNeJP55tG8sLYc2eyNhDxpw",
      traits = Some(Map("sdm.devices.traits.Humidity" -> Map("ambientHumidityPercent" -> 40.0.asInstanceOf[Object]))),
      events = None
    ),
    Seq("enterprises/0f9309d0-c49f-4bc7-ad34-f95106965b2b/devices/AVPHwEvo5ks2yhYAo9D8a0V-BVhI2qrOsx35LlCyxnsIjayu7JCKmuun-lq-W1VUshI7b_STNeJP55tG8sLYc2eyNhDxpw"),
    "AVPHwEseIbXjtxUtwjGaVS7rZFqZt3AuD1sGNxSv2i_D"
  )

  val personEvent = SdmEvent("e488e228-9a8b-4e59-b23c-0e721e02861a",
    "2021-04-18T23:07:23.230Z",
    ResourceUpdate(
      name = "enterprises/0f9309d0-c49f-4bc7-ad34-f95106965b2b/devices/AVPHwEs3PW_ufEm1HEuf-ae8nvaEtQcKxEz6Bu49Djd2I9XULjHp49rMUakllK-hX496hB1wUHwNnZw6AVZBtZRjheSTtA",
      traits = None,
      events = Some(Map("sdm.devices.events.CameraPerson.Person" ->
        Map(
          "eventSessionId" -> "AVPHwEtW4m8pSUh6weBjpLq2ViGoj798C8kjeAGP3974pdMAH7h0TSTjLdoGiD7y8G-VX8OMrGByfNZyuvhVvLHBDnKL4Q".asInstanceOf[Object],
          "eventId" -> "CiQA2vuxr-ilCPX3Rahts5UF2mgwJ-Gcz51ZlAcAxV2EA7wHbR8SwgEAq-xCHpkzVUj4nKrKOSXv0oSIcWmeGgLlj9fT6TfqpoA4gPqUhsGFFKzUwUd8JsRnT2kiRILtWAZijjFPlx7UEnfJ9ByIfVrJFt-W__D9beg86BzY5xY0nLyyBllLHFDjkxBurCisAOoTuhAtMpml2X-LBzU35T57hBDivpeb014oZmtAwVbDWN3Fmy2FuZvyCq7tOJpfCgd8kcMmNypYRKE7TYl4a4hjQ_b1Zjv3p4vw-u5qukJ7g8jSS8l-WMo5qQ"
        )
      ))
    ),
    Seq("enterprises/0f9309d0-c49f-4bc7-ad34-f95106965b2b/devices/AVPHwEs3PW_ufEm1HEuf-ae8nvaEtQcKxEz6Bu49Djd2I9XULjHp49rMUakllK-hX496hB1wUHwNnZw6AVZBtZRjheSTtA"),
    "AVPHwEseIbXjtxUtwjGaVS7rZFqZt3AuD1sGNxSv2i_D"
  )
}
