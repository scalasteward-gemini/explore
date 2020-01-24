package explore.graphql

import client.GraphQLQuery
import java.util.UUID
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

object ClearCompletedMutation extends GraphQLQuery {
  val document = """
      mutation ClearCompletedMutation {
        clearCompleted {
          id
        }
      }"""

  case class Variables()
  object Variables { implicit val jsonEncoder: Encoder[Variables] = deriveEncoder[Variables] }

  case class Data(clearCompleted: Option[List[ClearedTasks]])
  object Data { implicit val jsonDecoder: Decoder[Data] = deriveDecoder[Data] }

  case class ClearedTasks(id: Option[UUID])
  object ClearedTasks {
    implicit val jsonDecoder: Decoder[ClearedTasks] = deriveDecoder[ClearedTasks]
    implicit val jsonEncoder: Encoder[ClearedTasks] = deriveEncoder[ClearedTasks]    
  }

  implicit val jsonDecoder: Decoder[Response] = deriveDecoder[Response]
}