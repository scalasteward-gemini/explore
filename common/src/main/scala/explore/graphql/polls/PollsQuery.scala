package graphql.codegen
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import sangria.macros._
import types._
object polls {
  object PollsQuery extends GraphQLQuery {
    val document: sangria.ast.Document = graphql"""query PollsQuery {
  poll {
    id
    question
    options(order_by: {id: desc}) {
      id
      text
    }
  }
}"""
    case class Variables()
    object Variables { implicit val jsonEncoder: Encoder[Variables] = deriveEncoder[Variables] }
    case class Data(poll: List[Poll])
    object Data { implicit val jsonDecoder: Decoder[Data] = deriveDecoder[Data] }
    case class Poll(id: uuid, question: String, options: List[Poll.Options])
    object Poll {
      implicit val jsonDecoder: Decoder[Poll] = deriveDecoder[Poll]
      implicit val jsonEncoder: Encoder[Poll] = deriveEncoder[Poll]
      case class Options(id: uuid, text: String)
      object Options {
        implicit val jsonDecoder: Decoder[Options] = deriveDecoder[Options]
        implicit val jsonEncoder: Encoder[Options] = deriveEncoder[Options]
      }
    }
  }
}