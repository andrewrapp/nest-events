package domain

case class SdmEvent(eventId: String, timestamp: String, resourceUpdate: ResourceUpdate, resourceGroup: Seq[String], userId: String) {
    def isImageEvent() = {
        resourceUpdate.getEventsResources().contains("sdm.devices.events.CameraMotion.Motion") ||
          resourceUpdate.getEventsResources().contains("sdm.devices.events.CameraPerson.Person") ||
//          resourceUpdate.getEventsResources().contains("sdm.devices.events.CameraSound.Sound") ||
          resourceUpdate.getEventsResources().contains("sdm.devices.events.DoorbellChime.Chime")
    }

    def getHvacStatus(): Option[String] = {
        for {
            traits <- resourceUpdate.traits
            sdmTrait: Map[String, Object] <- traits.get(SdmTraits.HVAC)
            status <- sdmTrait.get("status").map(_.toString)
        } yield status
    }
}
