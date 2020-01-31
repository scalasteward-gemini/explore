package explore.model

import crystal._
import cats.effect.ConcurrentEffect
import cats.implicits._
import explore.graphql.polls._

trait PollsActions[F[_]] {
  def retrieveAll(): F[List[Poll]]
  def refresh(): F[Unit]
}

class PollsActionsInterpreter[F[_]: ConcurrentEffect](lens: FixedLens[F, List[Poll]])
    extends PollsActions[F] {

  def retrieveAll(): F[List[Poll]] =
    AppState.pollClient.query[F](PollsQuery)().map(_.poll)

  def refresh(): F[Unit] =
    for {
      polls <- retrieveAll()
      _     <- lens.set(polls)
    } yield ()

}
