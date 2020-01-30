package graphql.codegen
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import sangria.macros._
import types._
object pollResults {
  object PollResults extends GraphQLQuery {
    val document: sangria.ast.Document = graphql"""subscription PollResults($$pollId: uuid!) {
  poll_results(order_by: {option_id: desc}, where: {poll_id: {_eq: $$pollId}}) {
    option_id
    option {
      id
      text
    }
    votes
  }
}"""
    case class Variables(pollId: uuid)
    object Variables { implicit val jsonEncoder: Encoder[Variables] = deriveEncoder[Variables] }
    case class Data(poll_results: List[Poll_results])
    object Data { implicit val jsonDecoder: Decoder[Data] = deriveDecoder[Data] }
    case class Poll_results(option_id: Option[uuid], option: Option[Poll_results.Option], votes: Option[bigint])
    object Poll_results {
      implicit val jsonDecoder: Decoder[Poll_results] = deriveDecoder[Poll_results]
      implicit val jsonEncoder: Encoder[Poll_results] = deriveEncoder[Poll_results]
      case class Option(id: uuid, text: String)
      object Option {
        implicit val jsonDecoder: Decoder[Option] = deriveDecoder[Option]
        implicit val jsonEncoder: Encoder[Option] = deriveEncoder[Option]
      }
    }
  }
}