package explore.graphql.client

import cats.effect._
import org.scalajs.dom.ext.Ajax
import io.circe._
import io.circe.syntax._
import io.circe.parser._

case class AjaxGraphQLClient(uri: String)(implicit csIO: ContextShift[IO]) extends GraphQLClient {
    // Response
    // {
    //   "data": { ... }, // Typed
    //   "errors": [ ... ]
    // }

    protected def queryInternal[F[_] : LiftIO, V, D: Decoder](document: String, operationName: Option[String] = None, variables: Option[Json] = None): F[D] = 
        LiftIO[F].liftIO {
            IO.fromFuture(IO(
                Ajax.post(
                    url = uri,
                    data = GraphQLRequest(document, operationName = operationName, variables = variables).asJson.toString,
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
