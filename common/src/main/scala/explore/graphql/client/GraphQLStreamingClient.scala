package explore.graphql.client

import fs2.Stream
import io.circe.Decoder
import cats.effect.ConcurrentEffect

trait GraphQLStreamingClient {
    val uri: String

    def subscribe[F[_] : ConcurrentEffect, D : Decoder](subscription: String): F[Stream[F, D]]
}