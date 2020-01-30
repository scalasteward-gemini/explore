package explore.graphql.polls

import explore.graphql.client.GraphQLQuery
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

object newUser {
  object NewUserMutation extends GraphQLQuery {
    val document = """
      mutation NewUserMutation($uuid: uuid) {
        insert_user(objects: [{id: $$uuid}]) {
          returning {
            id
            created_at
          }
        }
      }
    """

    case class Variables(uuid: Option[uuid])
    object Variables { implicit val jsonEncoder: Encoder[Variables] = deriveEncoder[Variables] }

    case class Data(insert_user: Option[Insert_user])
    object Data { implicit val jsonDecoder: Decoder[Data] = deriveDecoder[Data] }

    case class Insert_user(returning: List[Insert_user.Returning])
    object Insert_user {
      implicit val jsonDecoder: Decoder[Insert_user] = deriveDecoder[Insert_user]
      implicit val jsonEncoder: Encoder[Insert_user] = deriveEncoder[Insert_user]

      case class Returning(id: uuid, created_at: timestamptz)
      object Returning {
        implicit val jsonDecoder: Decoder[Returning] = deriveDecoder[Returning]
        implicit val jsonEncoder: Encoder[Returning] = deriveEncoder[Returning]
      }
    }
  }
}