package explore.graphql.client

import cats.effect._
import cats.implicits._
import org.scalajs.dom.ext.Ajax
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import io.circe.Decoder

case class AjaxGraphQLClient(uri: String)(implicit csIO: ContextShift[IO]) extends GraphQLClient {
    // Request
    // {
    //   "query": "...",
    //   "operationName": "...",
    //   "variables": { "myVariable": "someValue", ... }
    // }

    private case class Request(
        query: String,
        operationName: Option[String] = None,
        variables: Option[Json] = None
    )

    // Response
    // {
    //   "data": { ... }, // Typed
    //   "errors": [ ... ]
    // }
    
    def query[F[_] : LiftIO](graphQLQuery: GraphQLQuery)(variables: Option[graphQLQuery.Variables] = None): F[graphQLQuery.Data] = {
        import graphQLQuery._
        
        variables.fold(query[F, graphQLQuery.Data](graphQLQuery.document)){v => 
            query[F, graphQLQuery.Variables, graphQLQuery.Data](graphQLQuery.document, v)}
    }

    def query[F[_] : LiftIO, V : Encoder, D : Decoder](document: String, variables: V, operationName: String): F[D] = {
        queryInternal[F, V, D](document, operationName.some, variables.asJson.some) 
    }

    def query[F[_] : LiftIO,  D: Decoder](document: String, operationName: String): F[D] = {
        queryInternal[F, Nothing, D](document, operationName.some)
    }

    def query[F[_] : LiftIO, V : Encoder, D : Decoder](document: String, variables: V): F[D] = {
        queryInternal[F, V, D](document, None, variables.asJson.some) 
    }

    def query[F[_] : LiftIO, D: Decoder](document: String): F[D] = {
        queryInternal[F, Nothing, D](document)
    }

    private def queryInternal[F[_] : LiftIO, V, D: Decoder](document: String, operationName: Option[String] = None, variables: Option[Json] = None): F[D] = 
        LiftIO[F].liftIO {
            IO.fromFuture(IO(
                Ajax.post(
                    url = uri,
                    data = Request(document, operationName = operationName, variables = variables).asJson.toString,
                    headers = Map("Content-Type" -> "application/json")
                    )
            ))
            .map(r => 
                parse(r.responseText)
                .flatMap(_.hcursor.downField("data").as[D])
                // TODO Handle errors
            )
            .flatMap(r => IO.fromEither(r))
        }
}
