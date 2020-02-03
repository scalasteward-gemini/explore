package explore.graphql.client

import fs2.Stream
import cats.effect._
import cats.implicits._
import io.circe._
import io.circe.parser._
import fs2.concurrent.Queue
import java.util.UUID
import scala.collection.mutable
import cats.effect.concurrent.Deferred
import cats.data.EitherT

trait ApolloStreamingClient extends GraphQLStreamingClient[ConcurrentEffect] {
  implicit val csIO: ContextShift[IO]

  type Subscription[F[_], D] = ApolloSubscription[F, D]

  case class ApolloSubscription[F[_]: LiftIO, D](stream: Stream[F, D], private val id: String)
      extends Stoppable[F] {
    def stop: F[Unit] =
      LiftIO[F].liftIO(client.get.map { sender =>
        subscriptions.get(id).foreach(_.terminate())
        subscriptions -= id
        sender.foreach(_.send(StreamingMessage.Stop(id)))
      })
  }

  private trait Emitter {
    def emitData(json:  Json): Unit
    def emitError(json: Json): Unit
    def terminate(): Unit
  }

  type DataQueue[F[_], D] = Queue[F, Either[Throwable, Option[D]]]

  private case class QueueEmitter[F[_]: Effect, D: Decoder](queue: DataQueue[F, D])
      extends Emitter {

    private def runEffect(effect: F[Unit]): Unit = {
      Effect[F].toIO(effect).unsafeRunAsyncAndForget()
    }

    def emitData(json: Json): Unit = {
      val data   = json.as[D]
      runEffect(queue.enqueue1(data.map(_.some)))
    }

    def emitError(json: Json): Unit = {
      val error  = new GraphQLException(List(json))
      runEffect(queue.enqueue1(Left(error)))
    }

    def terminate(): Unit = 
      runEffect(queue.enqueue1(Right(None)))
  }

  private val subscriptions: mutable.Map[String, Emitter] = mutable.Map.empty

  type WebSocketClient

  trait Sender {
    def send(msg: StreamingMessage): Unit
  }

  final protected def processMessage(str: String): Unit = 
    decode[StreamingMessage](str) match {
      case Left(e) =>
        // TODO Proper logging
        println(s"Exception decoding WebSocket message for [$uri]")
        e.printStackTrace()
      case Right(StreamingMessage.ConnectionError(json)) =>
        // TODO Proper logging
        println(s"Connection error on WebSocket for [$uri]: $json")
      case Right(StreamingMessage.DataJson(id, json)) =>
        subscriptions.get(id).foreach(_.emitData(json))
      case Right(StreamingMessage.Error(id, json)) =>
        println((id, json))
      case Right(StreamingMessage.Complete(id)) =>
        subscriptions.get(id).foreach(_.terminate())
      case _ =>
    }

  final protected def terminateAllSubscriptions(): Unit =
    subscriptions.foreach {
      case (id, emitter) =>
        emitter.terminate()
        subscriptions -= id
    }

  protected def createClient(
    onOpen: Sender => Unit,
    onMessage: String => Unit,
    onError: Exception => Unit): Unit


  lazy private val client: Deferred[IO, Either[Exception, Sender]] = {
    val deferred = Deferred.unsafe[IO, Either[Exception, Sender]]

    try {
      createClient(
        onOpen = { sender =>
          deferred
            .complete(Right(sender))
            .map(_ => sender.send(StreamingMessage.ConnectionInit()))
            .unsafeRunAsyncAndForget()
        },

        onMessage = processMessage _,

        onError = { exception =>
          deferred
            .complete(Left(exception))
            .recover {
              // Deferred was already complete. We must cancel all subscriptions.
              case _: IllegalStateException => terminateAllSubscriptions()
            }
            .unsafeRunAsyncAndForget()        
        }
      )
    } catch {
      case e: Exception =>
        deferred.complete(Left(e)).unsafeRunAsyncAndForget()
    }      

    deferred
  }

  private def buildQueue[F[_]: ConcurrentEffect, D: Decoder]: F[(String, DataQueue[F, D])] =
    for {
      queue <- Queue.unbounded[F, Either[Throwable, Option[D]]]
    } yield {
      val id      = UUID.randomUUID().toString
      val emitter = QueueEmitter(queue)
      subscriptions += (id -> emitter)
      (id, queue)
    }

  protected def subscribeInternal[F[_]: ConcurrentEffect, D: Decoder](
    subscription:  String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[Subscription[F, D]] =
    (for {
      sender <- EitherT(LiftIO[F].liftIO(client.get))
      idq    <- EitherT.right[Exception](buildQueue[F, D])
    } yield {
      val (id, q) = idq
      sender.send(
        StreamingMessage.Start(id, GraphQLRequest(subscription, operationName, variables))
      )
      ApolloSubscription(q.dequeue.rethrow.unNoneTerminate, id)
    }).value.rethrow

  protected def queryInternal[F[_]: ConcurrentEffect, D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D] =
    // Cleanup should happen automatically, as long as the server sends the "Complete" message.
    // We could add an option to force cleanup, in which case we would wrap the IO.asyncF in a Bracket.
    LiftIO[F].liftIO {
      IO.asyncF[D] { cb =>
        subscribeInternal[IO, D](document, operationName, variables).flatMap { subscription =>
          subscription.stream.attempt
            .head
            .evalMap(result => IO(cb(result)))
            .compile
            .drain
        }
      }
    }
}