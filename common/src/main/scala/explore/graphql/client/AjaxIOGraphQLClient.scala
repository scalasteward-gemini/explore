package explore.graphql.client

import cats.effect._
import cats.implicits._
import org.scalajs.dom.ext.Ajax
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import io.circe.Decoder

case class AjaxIOGraphQLClient(uri: String)(implicit csIO: ContextShift[IO]) extends GraphQLClient[IO] {
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
    
    def query(graphQLQuery: GraphQLQuery)(variables: Option[graphQLQuery.Variables] = None): IO[graphQLQuery.Data] = {
        import graphQLQuery._
        
        variables.fold(query[graphQLQuery.Data](graphQLQuery.document)){v => 
            query[graphQLQuery.Variables, graphQLQuery.Data](graphQLQuery.document, v)}
    }

    def query[V : Encoder, D : Decoder](document: String, variables: V, operationName: String): IO[D] = {
        queryInternal[V, D](document, operationName.some, variables.asJson.some) 
    }

    def query[D: Decoder](document: String, operationName: String): IO[D] = {
        queryInternal[Nothing, D](document, operationName.some)
    }

    def query[V : Encoder, D : Decoder](document: String, variables: V): IO[D] = {
        queryInternal[V, D](document, None, variables.asJson.some) 
    }

    def query[D: Decoder](document: String): IO[D] = {
        queryInternal[Nothing, D](document)
    }

    private def queryInternal[V, D: Decoder](document: String, operationName: Option[String] = None, variables: Option[Json] = None): IO[D] = {
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
