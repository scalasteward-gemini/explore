package explore.graphql.client

import io.circe.Decoder
import cats.effect.ConcurrentEffect

trait GraphQLStreamingClient {
    val uri: String

    protected trait Stoppable[F[_]] {
        def stop: F[Unit]
    }

    type Subscription[F[_], D] <: Stoppable[F]

    def subscribe[F[_] : ConcurrentEffect, D : Decoder](subscription: String): F[Subscription[F, D]]
}