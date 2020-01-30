package explore.graphql.client

import io.circe.Decoder

// Effects are purposely declared in individual methods instead of the trait.
// This is so that the methods can be easily called from tagless code.
trait GraphQLStreamingClient[E[_[_]]] extends GraphQLClient[E] {
    val uri: String

    protected trait Stoppable[F[_]] {
        def stop: F[Unit]
    }

    type Subscription[F[_], D] <: Stoppable[F]

    def subscribe[F[_] : E, D : Decoder](subscription: String): F[Subscription[F, D]]
}