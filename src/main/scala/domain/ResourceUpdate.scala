package domain

import scala.util.control.NonFatal

case class ResourceUpdate(name: String, traits: Option[Map[String, Map[String, Object]]], events: Option[Map[String, Map[String, Object]]]) {
  // device id is last component "/" delimited in name
  // ex enterprises/0f9309d0-c49f-4bc7-ad34-f95106965b2b/devices/AVPHwEs3PW_ufEm1HEuf-ae8nvaEtQcKxEz6Bu49Djd2I9XULjHp49rMUakllK-hX496hB1wUHwNnZw6AVZBtZRjheSTtA
  def getDeviceId(): String = {
    name.split("/")(3);
  }

  // the eventId of the device, not message
  def getDeviceEventId(eventType: String): String = {
    events.getOrElse(throw new Exception(s"event ${eventType} missing from event ${this}")).get(eventType).get("eventId").toString
  }

  def getTraitResources(): Set[String] =  traits.map(t => t.keySet).getOrElse(Set())

  def getEventsResources(): Set[String] = events.map(_.keySet).getOrElse(Set())

  def getResources(): Set[String] = getTraitResources() ++ getEventsResources()

  def getResourcesCsv() = getResources().mkString(",")
}
