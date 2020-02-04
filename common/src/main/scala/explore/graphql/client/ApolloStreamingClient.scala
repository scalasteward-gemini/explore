package explore.graphql.client

import fs2.Stream
import cats.effect._
import cats.implicits._
import io.circe._
import io.circe.parser._
import fs2.concurrent.Queue
import java.util.UUID
import scala.collection.mutable
import cats.effect.concurrent.MVar
import cats.data.EitherT
import scala.concurrent.duration._
import scala.language.postfixOps

trait ApolloStreamingClient extends GraphQLStreamingClient[ConcurrentEffect] {
  implicit val timerIO: Timer[IO]
  implicit val csIO: ContextShift[IO]

  type Subscription[F[_], D] = ApolloSubscription[F, D]

  case class ApolloSubscription[F[_]: LiftIO, D](
    stream: Stream[F, D],
    private val id: String) extends Stoppable[F] {

      def stop: F[Unit] =
        LiftIO[F].liftIO(client.read.map { sender =>
          subscriptions.get(id).foreach(_.terminate())
          subscriptions -= id
          sender.foreach(_.send(StreamingMessage.Stop(id)))
        })
  }

  private trait Emitter {
    val request: GraphQLRequest

    def emitData(json:  Json): Unit
    def emitError(json: Json): Unit
    def terminate(): Unit
  }

  type DataQueue[F[_], D] = Queue[F, Either[Throwable, Option[D]]]

  private case class QueueEmitter[F[_]: Effect, D: Decoder](
    val queue: DataQueue[F, D],
    val request: GraphQLRequest) extends Emitter {

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

  protected type WebSocketClient

  protected trait Sender {
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

  protected def createClientInternal(
    onOpen: Sender => Unit,
    onMessage: String => Unit,
    onError: Exception => Unit,
    onClose: Boolean => Unit // Boolean = wasClean
  ): Unit

  private def createClient(mvar: MVar[IO, Either[Exception, Sender]]): Unit = {
    try {
      createClientInternal(
        onOpen = { sender =>
          mvar
            .tryPut(Right(sender))
            .map {
              case true => sender.send(StreamingMessage.ConnectionInit())
              case false => // TODO Handle Error
            }
            .unsafeRunAsyncAndForget()
        },

        onMessage = processMessage _,

        onError = { exception =>
          mvar
            .tryPut(Left(exception))
            .map{
              // Deferred was already complete. We must cancel all subscriptions.
              case false => terminateAllSubscriptions()
              case true => // Do nothing
            }
            .unsafeRunAsyncAndForget()        
        },

        onClose = { _ =>
          (for {
            _ <- mvar.take
            _ <- IO.sleep(10 seconds) // TODO: Backoff.
            _ <- IO(createClient(mvar))
            sender <- mvar.read
          } yield(
            // Restart subscriptions on new client.
            subscriptions.foreach{ case (id, emitter) =>
              sender.foreach(_.send(StreamingMessage.Start(id, emitter.request)))
            }
          )).unsafeRunAsyncAndForget()          
        }
      )
    } catch {
      case e: Exception =>
        mvar.put(Left(e)).unsafeRunAsyncAndForget() // TODO: Use tryPut and handle error
    }
  }

  lazy private val client: MVar[IO, Either[Exception, Sender]] = {
    val mvar = MVar.emptyIn[SyncIO, IO, Either[Exception, Sender]].unsafeRunSync()

    createClient(mvar)

    mvar
  }

  private def buildQueue[F[_]: ConcurrentEffect, D: Decoder](request: GraphQLRequest): F[(String, QueueEmitter[F, D])] =
    for {
      queue <- Queue.unbounded[F, Either[Throwable, Option[D]]]
    } yield {
      val id      = UUID.randomUUID().toString
      val emitter = QueueEmitter(queue, request)
      subscriptions += (id -> emitter)
      (id, emitter)
    }

  protected def subscribeInternal[F[_]: ConcurrentEffect, D: Decoder](
    subscription:  String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[Subscription[F, D]] = {
    val request = GraphQLRequest(subscription, operationName, variables)

    (for {
      sender <- EitherT(LiftIO[F].liftIO(client.read))
      idEmitter <- EitherT.right[Exception](buildQueue[F, D](request))
      (id, emitter) = idEmitter
    } yield {
      sender.send(StreamingMessage.Start(id, request))
      ApolloSubscription(emitter.queue.dequeue.rethrow.unNoneTerminate, id)
    }).value.rethrow
  }

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