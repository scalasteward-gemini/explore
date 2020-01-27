package explore.graphql.client

import cats.effect._

trait GraphQLClient {
  val uri: String
  def query[F[_] : LiftIO](query: GraphQLQuery)(variables: Option[query.Variables]): F[query.Data]
}
