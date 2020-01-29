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
import cats.effect.concurrent.Deferred

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

    private case class WebSocketSender(private val ws: WebSocket) {
        def send(msg: StreamingMessage): Unit =
            ws.send(msg.asJson.toString)
    }

    lazy private val client: Deferred[IO, WebSocketSender] = {
        val ws = new WebSocket(uri, Protocol)
        val deferred = Deferred.unsafe[IO, WebSocketSender]

        ws.onopen = {_: Event =>
            val sender = WebSocketSender(ws)
            deferred.complete(sender).map( _ =>
                sender.send(ConnectionInit())
            ).unsafeRunAsyncAndForget()
        }

        ws.onmessage = {e: MessageEvent =>
            e.data match { 
                case str: String => 
                    val msg = decode[StreamingMessage](str)
                    println(msg)
                case other => println(s"Unexpected event from WebSocket [$uri]: [$other]")
            }
        }

        deferred
    }

    private def buildQueue[F[_] : ConcurrentEffect, D : Decoder]: F[(String, Queue[F, D])] = {
        for {
            queue <- Queue.unbounded[F, D]
        } yield {
            val id = UUID.randomUUID().toString
            val emitter = QueueEmitter(queue)
            subscriptions += (id -> emitter)
            (id, queue)
        }
    }

    def subscribe[F[_] : ConcurrentEffect, D : Decoder](subscription: String): F[Stream[F, D]] = {
        for {
            idq <- buildQueue[F, D]
            (id, q) = idq
            sender <- LiftIO[F].liftIO(client.get)
        } yield {
            sender.send(Start(id, GraphQLRequest(query = subscription)))
            q.dequeue
        }
    }
}