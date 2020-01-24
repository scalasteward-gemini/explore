package explore.graphql

import client.GraphQLQuery
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import explore.model.Task

object AllTasksQuery extends GraphQLQuery {
  val document = """
      query AllTasksQuery {
        todos {
          id
          title
          completed
        }
      }"""

  case class Variables()
  object Variables { implicit val jsonEncoder: Encoder[Variables] = deriveEncoder[Variables] }

  case class Data(todos: Option[List[Task]])
  object Data { implicit val jsonDecoder: Decoder[Data] = deriveDecoder[Data] }

  implicit val jsonDecoder: Decoder[Response] = deriveDecoder[Response]
}
