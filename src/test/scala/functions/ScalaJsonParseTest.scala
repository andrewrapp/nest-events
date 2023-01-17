package functions

import com.google.inject.name.Names
import com.google.inject.{Guice, Injector, Key}
import com.twitter.finatra.jackson.ScalaObjectMapper
import domain.{ListStructures, PubsubMessage, SdmEvent, ThermostatStats}
import modules.SerializationModule
import org.junit.Assert.assertEquals
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite

import scala.io.Source

class ScalaJsonParseTest extends AnyFunSuite with BeforeAndAfterEach {
  val injector: Injector = Guice.createInjector(new SerializationModule())

  val camelCaseMapper: ScalaObjectMapper = injector.getInstance(Key.get(classOf[ScalaObjectMapper], Names.named("CamelCaseObjectMapper")))

  test("parse list structures") {

    // TODO load resource from classloader
    val json = Source.fromFile("src/test/resources/list-devices.json").getLines.mkString

    val structures = camelCaseMapper.parse[ListStructures](json)

    val stats: ThermostatStats = structures.getThermoStats()

    assert(stats.mode == Some("HEAT"))
    assert(stats.relativeHumidity == Some(40))
    assert(stats.temperatureFahrenheit == Some(69.0619928))
    assert(stats.heatSetPointFahrenheit == Some(69.0291176))
    assert(stats.coolSetPointFahrenheit == None)
  }

  test("parse pubsub message") {

    val json = Source.fromFile("src/test/resources/pubsub.json").getLines.mkString
    val pubsubMessage = camelCaseMapper.parse[PubsubMessage](json)

    camelCaseMapper.parse[SdmEvent](pubsubMessage.message.getDataJson())

    assert (pubsubMessage.subscription == "projects/ubiquiti-290423/subscriptions/nestapi")
    assert (pubsubMessage.message.messageId == "2347950029047046")
    assert (pubsubMessage.message.publishTime == "2021-05-01T15:05:32.131Z")
  }

  test("parse pubsub trait") {
    val json = Source.fromFile("src/test/resources/multi-trait-pubsub.json").getLines.mkString

    val traits = camelCaseMapper.parse[SdmEvent](json)

    assert (traits.resourceUpdate.getResources() == Set("sdm.devices.traits.ThermostatMode", "sdm.devices.traits.ThermostatEco", "sdm.devices.traits.ThermostatTemperatureSetpoint"))
  }

  test("parse pubsub hvac off") {
    val json = Source.fromFile("src/test/resources/sdm-hvac-off.json").getLines.mkString
    val events = camelCaseMapper.parse[SdmEvent](json)

    assert (events.resourceUpdate.getResources() == Set("sdm.devices.traits.ThermostatHvac"))
    assert(events.resourceUpdate.traits.get.get("sdm.devices.traits.ThermostatHvac").get("status") == "OFF")
    assert(events.resourceUpdate.traits.get.get("sdm.devices.traits.ThermostatHvac").get.keySet.size == 1)
  }

  test("parse pubsub temperature") {
    val json = Source.fromFile("src/test/resources/sdm-temperature-pubsub.json").getLines.mkString
    val events = camelCaseMapper.parse[SdmEvent](json)

    assert (events.resourceUpdate.getResources() == Set("sdm.devices.traits.Temperature"))
    assert(events.resourceUpdate.traits.get.get("sdm.devices.traits.Temperature").get("ambientTemperatureCelsius") == 20.669998)
    assert(events.resourceUpdate.traits.get.get("sdm.devices.traits.Temperature").get.keySet.size == 1)
  }

  test("parse pubsub event") {
    val json = Source.fromFile("src/test/resources/sdm-camera-person-event.json").getLines.mkString
    val events = camelCaseMapper.parse[SdmEvent](json)

    assert (events.resourceUpdate.getResources() == Set("sdm.devices.events.CameraPerson.Person"))
  }

  test("parse pubsub motion event") {
    val json = Source.fromFile("src/test/resources/sdm-camera-motion-event.json").getLines.mkString
    val sdmEvent = camelCaseMapper.parse[SdmEvent](json)

    val eventType: String = sdmEvent.resourceUpdate.events.get.keySet.head

    assertEquals(eventType, "sdm.devices.events.CameraMotion.Motion")

    assertEquals(sdmEvent.resourceUpdate.events.get.get("sdm.devices.events.CameraMotion.Motion")
      .get("eventSessionId"), "AVPHwEvq3pLNztoIYvSljVDOfzqR2luJvpT_9pbtoHnYN66zPAxBjgoipCmqPYLdjiieHAJtCsQv-57SyWX1bKpJoLUs3A")

    assertEquals(
      sdmEvent.resourceUpdate.getDeviceEventId("sdm.devices.events.CameraMotion.Motion"),
      "CiQA2vuxr-9URIjRYjbMDBNvVkCF7BcFbYTWXSqYj6mdCnujMnsSvgEAq-xCHmrvJ_ITp55Yn0M8BIHAsL_3-h2wCDU03eDzEsqiSwC77_BQJvq0Js3uuecpvOn0moLN7BrFVQ4vnFjy-DQuJdUGWQXw_F_ZZjrqOeuYdd8k9o5Cu2xpd19Az8xkbSSjMbU695M4BZKtVOokSjGf1qrtQ-FbxTqAGzwFobs3uY7_8sfowxQyCHAO0VBhwn-WvZD7sr0XcHdLGAwdyOvzq72r8_fQG_M2ed4gMlVoduROfDk3QyMah_GV"
    )
  }

  test("parse pubsub humidity event") {
    val json = Source.fromFile("src/test/resources/sdm-humidity-event.json").getLines.mkString
    val sdmEvent = camelCaseMapper.parse[SdmEvent](json)

    assertEquals(sdmEvent.resourceUpdate.traits.get
      .get("sdm.devices.traits.Humidity")
      .get("ambientHumidityPercent").asInstanceOf[Double].toString,
      "41.0"
    )

    assertEquals(
      sdmEvent.resourceUpdate.getDeviceId(),
      "AVPHwEvo5ks2yhYAo9D8a0V-BVhI2qrOsx35LlCyxnsIjayu7JCKmuun-lq-W1VUshI7b_STNeJP55tG8sLYc2eyNhDxpw"
    )

    assertEquals(
      sdmEvent.resourceUpdate.getTraitResources().mkString(""),
      "sdm.devices.traits.Humidity"
    )
  }
}
