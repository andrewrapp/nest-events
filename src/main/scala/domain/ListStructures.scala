package domain

import com.twitter.inject.Logging
import com.twitter.util.Try
import com.twitter.util.logging.Logger
import domain.Logging.logger

object Logging {
  val logger: Logger = com.twitter.util.logging.Logger.getLogger("ListStructures")
}

case class ThermostatStats(mode: Option[String], relativeHumidity: Option[Integer], temperatureFahrenheit: Option[Double], heatSetPointFahrenheit: Option[Double], coolSetPointFahrenheit: Option[Double])
case class Device(name: String, `type`: String, assignee: String, traits: Map[String,Object], parentRelations: Option[List[Map[String,String]]])
case class ListStructures(devices: List[Device]) {

  val ThermostatDevice = "sdm.devices.types.THERMOSTAT"

  def toFahrenheit(celsius: Double): Double = (celsius * 9.0/5.0) + 32

  /**
   * Extract relevant thermostat stats from list structures
   * @return
   */
  def getThermoStats(): ThermostatStats = {
    val thermostat: Device = devices
      .find(_.`type` == ThermostatDevice)
      .getOrElse(throw new Exception(s"${ThermostatDevice} not found in list structures ${devices}"))
    // not clear what params are optional/required
    // sometimes it's double, sometimes integer
    def convertObjectToDouble(obj: Object): Double = {
      if (obj.isInstanceOf[Double]) {
        obj.asInstanceOf[Double]
      } else if (obj.isInstanceOf[Int]) {
        obj.asInstanceOf[Int].toDouble
      } else {
        throw new Exception(s"Unable to convert to double ${obj}")
      }
    }

    val mode: Option[String] = thermostat.traits("sdm.devices.traits.ThermostatMode").asInstanceOf[Map[String,String]].get("mode")
      val humidity: Option[Integer] = thermostat.traits("sdm.devices.traits.Humidity").asInstanceOf[Map[String,Integer]].get("ambientHumidityPercent")
      val temperature: Option[Double] = Try(
          thermostat.traits("sdm.devices.traits.Temperature").asInstanceOf[Map[String,Object]]
          .get("ambientTemperatureCelsius")
          .map(obj => toFahrenheit(convertObjectToDouble(obj)))
        )
        .onFailure(t => logger.error(s"Failed to extract ambientTemperatureCelsius from ${thermostat.traits("sdm.devices.traits.Temperature")}"))
        .toOption
        .flatten

      val tempSetpoint: Map[String, Object] = thermostat.traits("sdm.devices.traits.ThermostatTemperatureSetpoint").asInstanceOf[Map[String,Object]]

      val heatSetPoint: Option[Double] = Try(tempSetpoint.get("heatCelsius").map(obj => toFahrenheit(convertObjectToDouble(obj))))
        .onFailure(t => logger.error(s"Failed to extract heatCelsius from ${tempSetpoint}", t))
        .toOption
        .flatten

      val coolSetPoint: Option[Double] = Try(tempSetpoint.get("coolCelsius").map(obj => toFahrenheit(convertObjectToDouble(obj))))
        .onFailure(t => logger.error(s"Failed to extract coolCelsius from ${tempSetpoint}", t))
        .toOption
        .flatten

      // Note: we'll leave off heating status for now since we'll be polling this metric every 15m or so and the pubsub receiver will get on/off events
      ThermostatStats(
        mode = mode,
        relativeHumidity = humidity,
        temperatureFahrenheit = temperature,
        heatSetPointFahrenheit = heatSetPoint,
        coolSetPointFahrenheit = coolSetPoint
      )
  }
}