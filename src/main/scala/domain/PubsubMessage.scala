package domain

import java.util.Base64

case class PubsubMessage(message: Message, subscription: String)

case class Message(
                          attributes: Option[Map[String, String]],
                          messageId: String,
                          publishTime: String,
                          data: String,
                          publish_time: String,
                          message_id: String
                        ) {

  def decode(data: String) = {
    new String(Base64.getDecoder().decode(data));
  }

  def getDataJson(): String = {
    decode(data);
  }
}