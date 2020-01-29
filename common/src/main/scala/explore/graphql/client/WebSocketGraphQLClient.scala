package explore.graphql.client

import fs2.Stream
import cats.effect._
import cats.implicits._
import org.scalajs.dom.raw.WebSocket
import scala.scalajs.js
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import fs2.concurrent.Queue
import java.util.UUID
import scala.collection.mutable
import org.scalajs.dom.raw.Event
import org.scalajs.dom.raw.MessageEvent

case class WebSocketGraphQLClient(uri: String)(implicit csIO: ContextShift[IO]) extends GraphQLStreamingClient {
    println(csIO)

    // case class Payload[D : Decoder](data: D)

    // case class Data[D : Decoder](id: String, payload: Payload[D])


    private trait Emitter {
        def emitData(data: js.Any): Unit
    }

    private case class QueueEmitter[F[_] : Effect, D : Decoder](queue: Queue[F, D]) extends Emitter {
        def emitData(data: js.Any): Unit = {
            val str = js.JSON.stringify(data)
            val d = parse(str).flatMap(_.as[D])                        
            val effect = queue.enqueue1(d.getOrElse[D](null.asInstanceOf[D]))
            Effect[F].toIO(effect).unsafeRunAsyncAndForget()
        }
    }

    private val subscriptions: mutable.Map[String, Emitter] = mutable.Map.empty

    private val Protocol = "graphql-ws"

    lazy private val client = {
        val ws = new WebSocket(uri, Protocol)

        def send(msg: StreamingMessage): Unit =
            ws.send(msg.asJson.toString)

        ws.onopen = {_: Event =>
            send(ConnectionInit())
        }

        ws.onmessage = {e: MessageEvent =>
            e.data match { 
                case str: String => 
                    val msg = decode[StreamingMessage](str)
                    println(msg)
                case other => println(s"Unexpected event from WebSocket [$uri]: [$other]")
            }
        }

        ws
    }

    private def buildQueue[F[_] : ConcurrentEffect, D : Decoder]: F[Queue[F, D]] = {
        for {
            queue <- Queue.unbounded[F, D]
            _ <- Sync[F].delay {
                val id = UUID.randomUUID()
                val emitter = QueueEmitter(queue)
                subscriptions += (id.toString -> emitter)
            }
        } yield queue
    }

    def subscribe[F[_] : ConcurrentEffect, D : Decoder](subscription: String): Stream[F, D] = {

        println(subscription)
        println(client)
        // client.send subscription

        for {
            q <- Stream.eval(buildQueue[F, D])
            d <- q.dequeue//.rethrow
        } yield d
    }
}