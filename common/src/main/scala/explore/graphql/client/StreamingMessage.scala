package explore.graphql.client

import io.circe.Encoder
import io.circe.Json
import io.circe.Decoder
import io.circe.HCursor
import io.circe.syntax._

// import io.circe.generic.extras._
// import scala.scalajs.js
// import io.circe._
// import io.circe.generic.semiauto._
// import io.circe.generic.JsonCodec
// import io.circe.generic.extras.JsonKey

// @JsonCodec
protected[client] sealed trait StreamingMessage {
    val tpe: MessageType
}

protected[client] sealed trait PayloadMessage[P] extends StreamingMessage {
    val payload: P

    implicit val encoder: Encoder[P]
}

protected[client] final case class ConnectionInit(payload: Map[String, String] = Map.empty)
    (implicit val encoder: Encoder[Map[String, String]]) extends PayloadMessage[Map[String, String]] {
    val tpe = "connection_init"
}

protected[client] case object ConnectionAck extends StreamingMessage {
    val tpe = "connection_ack"
}

protected[client] case object KeepAlive extends StreamingMessage {
    val tpe = "ka"
}

object StreamingMessage {
    implicit val encoder: Encoder[StreamingMessage] = new Encoder[StreamingMessage] {
        final def apply(msg: StreamingMessage): Json =
            Json.obj(
                "type" -> Json.fromString(msg.tpe)
            ).deepMerge(
                msg match {
                    case payloadMsg: PayloadMessage[_] => 
                        Json.obj("payload" -> payloadMsg.payload.asJson(payloadMsg.encoder))
                    case _ => Json.obj()
                }
            )
    }

    implicit val decoder: Decoder[StreamingMessage] = new Decoder[StreamingMessage] {
        final def apply(c: HCursor): Decoder.Result[StreamingMessage] = {
            for {
                tpe <- c.downField("type").as[MessageType]
            } yield {
                tpe match {
                    case ConnectionAck.tpe => ConnectionAck
                    case KeepAlive.tpe => KeepAlive
                }
            }
        }
    }
}