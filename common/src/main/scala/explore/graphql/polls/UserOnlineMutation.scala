package graphql.codegen
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import sangria.macros._
import types._
object userOnline {
  object UserOnlineMutation extends GraphQLQuery {
    val document: sangria.ast.Document = graphql"""mutation UserOnlineMutation($$uuid: uuid) {
  update_user(where: {id: {_eq: $$uuid}}, _set: {online_ping: true}) {
    affected_rows
    returning {
      last_seen_at
    }
  }
}"""
    case class Variables(uuid: Option[uuid])
    object Variables { implicit val jsonEncoder: Encoder[Variables] = deriveEncoder[Variables] }
    case class Data(update_user: Option[Update_user])
    object Data { implicit val jsonDecoder: Decoder[Data] = deriveDecoder[Data] }
    case class Update_user(affected_rows: Int, returning: List[Update_user.Returning])
    object Update_user {
      implicit val jsonDecoder: Decoder[Update_user] = deriveDecoder[Update_user]
      implicit val jsonEncoder: Encoder[Update_user] = deriveEncoder[Update_user]
      case class Returning(last_seen_at: Option[timestamptz])
      object Returning {
        implicit val jsonDecoder: Decoder[Returning] = deriveDecoder[Returning]
        implicit val jsonEncoder: Encoder[Returning] = deriveEncoder[Returning]
      }
    }
  }
}