package explore.graphql.client

import fs2.Stream
import cats.effect._
import cats.implicits._
import org.scalajs.dom.raw.WebSocket
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

    type Subscription[F[_], D] = WebSocketSubscription[F, D]

    case class WebSocketSubscription[F[_] : LiftIO, D](stream: Stream[F, D], private val id: String) extends Stoppable[F] {
        def stop: F[Unit] = {
            LiftIO[F].liftIO(client.get.map{sender => 
                subscriptions -= id
                sender.send(Stop(id))
            })
        }
    }

    private trait Emitter {
        def emitData(json: Json): Unit
    }

    private case class QueueEmitter[F[_] : Effect, D : Decoder](queue: Queue[F, D]) extends Emitter {
        def emitData(json: Json): Unit = {
            val data = json.as[D]
            val effect = queue.enqueue1(data.getOrElse[D](null.asInstanceOf[D]))
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
                    msg match {
                        case Right(DataJson(id, json)) =>
                            subscriptions.get(id).foreach(_.emitData(json))
                        case _ =>
                    }
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

    def subscribe[F[_] : ConcurrentEffect, D : Decoder](subscription: String): F[Subscription[F, D]] = {
        for {
            idq <- buildQueue[F, D]
            (id, q) = idq
            sender <- LiftIO[F].liftIO(client.get)
        } yield {
            sender.send(Start(id, GraphQLRequest(query = subscription)))
            WebSocketSubscription(q.dequeue, id)
        }
    }
}