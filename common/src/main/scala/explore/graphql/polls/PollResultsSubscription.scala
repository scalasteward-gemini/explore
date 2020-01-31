package explore.graphql.polls

import explore.graphql.client.GraphQLQuery
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import java.util.UUID
import explore.model.PollOption

object PollResultsSubscription extends GraphQLQuery {
  val document = """
    subscription PollResults($pollId: uuid!) {
      poll_results(order_by: {votes: desc}, where: {poll_id: {_eq: $pollId}}) {
        option {
          id
          text
        }
        votes
      }
    }
    """

  case class Variables(pollId: UUID)
  object Variables { implicit val jsonEncoder: Encoder[Variables] = deriveEncoder[Variables] }

  case class Data(poll_results: List[Poll_result])
  object Data { implicit val jsonDecoder: Decoder[Data] = deriveDecoder[Data] }

  case class Poll_result(
    option_id: Option[UUID],
    option:    Option[PollOption],
    votes:     Option[Long]
  )
  object Poll_result {
    implicit val jsonDecoder: Decoder[Poll_result] = deriveDecoder[Poll_result]
    implicit val jsonEncoder: Encoder[Poll_result] = deriveEncoder[Poll_result]
  }

  implicit val varEncoder: Encoder[Variables] = Variables.jsonEncoder
  implicit val dataDecoder: Decoder[Data]     = Data.jsonDecoder
}
