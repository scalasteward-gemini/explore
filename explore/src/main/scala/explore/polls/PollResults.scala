package explore.polls

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import java.util.UUID
import explore.model.AppState._
import cats.implicits._
import cats.effect.IO
import crystal._
import crystal.react.StreamRenderer
// import crystal.react.io.implicits._
import explore.graphql.polls.PollResultsSubscription
import scala.util.control.NonFatal

final case class PollResults(pollId: UUID) extends ReactProps {
  @inline def render: VdomElement = PollResults.component(this)
}

object PollResults {
  type Props = PollResults

  type Results = List[PollResultsSubscription.Poll_result]

  final case class State(
    subscription: Option[pollClient.Subscription[IO, PollResultsSubscription.Data]] = None,
    renderer:     Option[StreamRenderer[Results]]                                   = None
  )

  @inline implicit def io2Callback[A](io: IO[A]): Callback = Callback {
    io.unsafeRunAsyncAndForget()
  }

  implicit class CallbackToOps[A](val self: CallbackTo[A]) {
    def toIO: IO[A] = IO(self.runNow())
  }

  implicit class StateAccessorIOOps[S](private val self: StateAccess[CallbackTo, S]) {

    /** Provides access to state `S` in an `IO` */
    def stateIO: IO[S] =
      self.state.toIO
  }

  implicit class ModStateWithPropsIOOps[S, P](
    private val self: StateAccess.WriteWithProps[CallbackTo, P, S]
  ) {

    def setStateIO(s: S): IO[Unit] =
      IO.async[Unit] { cb =>
        val doMod = self.setState(s, Callback(cb(Right(()))))
        try doMod.runNow()
        catch {
          case NonFatal(t) => cb(Left(t))
        }
      }

    /**
      * Like `modState` but completes with a `Unit` value *after* the state modification has
      * been completed. In contrast, `modState(mod).toIO` completes with a unit once the state
      * modification has been enqueued.
      *
      * Provides access to both state and props.
      */
    def modStateIO(mod: S => S): IO[Unit] =
      IO.async[Unit] { cb =>
        val doMod = self.modState(mod, Callback(cb(Right(()))))
        try doMod.runNow()
        catch {
          case NonFatal(t) => cb(Left(t))
        }
      }

    /**
      * Like `modState` but completes with a `Unit` value *after* the state modification has
      * been completed. In contrast, `modState(mod).toIO` completes with a unit once the state
      * modification has been enqueued.
      *
      * Provides access to both state and props.
      */
    def modStateIO(mod: (S, P) => S): IO[Unit] =
      IO.async[Unit] { cb =>
        val doMod = self.modState(mod, Callback(cb(Right(()))))
        try doMod.runNow()
        catch {
          case NonFatal(t) => cb(Left(t))
        }
      }
  }

  private val component =
    ScalaComponent
      .builder[Props]("PollResults")
      .initialState(State())
      .render_S { state =>

        println(s"RESULTS STATE: $state")

        <.div(
          state.renderer.whenDefined(
            _ { results =>
              <.ol(
                results.toTagMod{ result =>
                  (for {
                      option <- result.option
                      votes <- result.votes
                    } yield (option.text, votes)
                  ).whenDefined{ case (text, votes) => <.li(s"$text: $votes")}
                }
              )
            }
          )
        )
      }
      .componentWillMount { $ =>
        pollClient
          .subscribe[IO](PollResultsSubscription)(
            PollResultsSubscription.Variables($.props.pollId).some
          )
          .flatMap { subscription =>
            $.modStateIO(_ =>
              State(subscription.some,
                    StreamRenderer.build(subscription.stream.map(_.poll_results)
                      // .attempt
                      // .evalTap(x => IO(println(x)))
                      // .rethrow
                      ).some)
            )
          }
      }
      .componentWillUnmount { $ =>
        $.state.subscription.fold(Callback.empty)(_.stop)
      }
      .build
}
