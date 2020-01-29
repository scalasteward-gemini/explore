package explore.graphql.client

// import io.circe.Encoder
// import io.circe.Json
// import io.circe.Decoder
// import io.circe.HCursor
// import io.circe.syntax._

import io.circe.generic.extras._
// import scala.scalajs.js
// import io.circe._
// import io.circe.generic.semiauto._
// import io.circe.generic.JsonCodec
// import io.circe.generic.extras.JsonKey

// @JsonCodec
@ConfiguredJsonCodec
protected[client] sealed trait StreamingMessage

protected[client] sealed trait PayloadMessage[P] extends StreamingMessage {
    val payload: P
}

protected[client] final case class ConnectionInit(payload: Map[String, String] = Map.empty)
        extends PayloadMessage[Map[String, String]]

protected[client] case object ConnectionAck extends StreamingMessage

protected[client] case object KeepAlive extends StreamingMessage

protected[client] sealed trait SubscriptionMessage extends StreamingMessage {
    val id: String
}

protected[client] final case class Start(id: String, payload: GraphQLRequest) 
    extends SubscriptionMessage with PayloadMessage[GraphQLRequest]

protected[client] final case class Stop(id: String) extends SubscriptionMessage

/*object StreamingMessage {
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
}*/