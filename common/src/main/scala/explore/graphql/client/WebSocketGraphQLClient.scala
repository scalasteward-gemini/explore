package explore.graphql.client

import cats.effect._
import org.scalajs.dom.raw.WebSocket
import io.circe.syntax._
import io.circe.parser._
import scala.scalajs.js
import org.scalajs.dom.raw.{ CloseEvent, Event, MessageEvent }

// This implementation follows the Apollo protocol, specified in:
// https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
// Also see: https://medium.com/@rob.blackbourn/writing-a-graphql-websocket-subscriber-in-javascript-4451abb9cd60
case class WebSocketGraphQLClient(uri: String)(
  implicit val timerIO:                Timer[IO],
  val csIO:                            ContextShift[IO]
) extends ApolloStreamingClient {

  private val Protocol = "graphql-ws"

  type WebSocketClient = WebSocket

  private case class WebSocketSender(private val ws: WebSocketClient) extends Sender {
    def send(msg: StreamingMessage): Unit =
      ws.send(msg.asJson.toString)
  }

  protected def createClientInternal(
    onOpen:    Sender => IO[Unit],
    onMessage: String => IO[Unit],
    onError:   Exception => IO[Unit],
    onClose:   Boolean => IO[Unit]
  ): IO[Unit] = IO {
    val ws = new WebSocket(uri, Protocol)

    js.timers.setTimeout(20000)(ws.close())

    ws.onopen = { _: Event =>
      onOpen(WebSocketSender(ws)).unsafeRunAsyncAndForget()
    }

    ws.onmessage = { e: MessageEvent =>
      e.data match {
        case str: String => onMessage(str).unsafeRunAsyncAndForget()
        case other       =>
          // TODO Proper logging
          println(s"Unexpected event from WebSocket for [$uri]: [$other]")
      }
    }

    ws.onerror = { e: Event =>
      val exception = parse(js.JSON.stringify(e)).map(json => new GraphQLException(List(json)))
      onError(
        exception
          .getOrElse[Exception](exception.swap.getOrElse(new Exception("Unexpected empty Either")))
      ).unsafeRunAsyncAndForget()
    }

    ws.onclose = { e: CloseEvent =>
      // Reconnect
      println(s"CONNECTION CLOSED! WASCLEAN [${e.wasClean}]")
      onClose(e.wasClean).unsafeRunAsyncAndForget()
    }
  }
}
