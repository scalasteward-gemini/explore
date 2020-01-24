package explore.model

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import java.util.UUID

case class Task(id: UUID, title: String, completed: Boolean)
object Task {
    implicit val jsonDecoder: Decoder[Task] = deriveDecoder[Task]
    implicit val jsonEncoder: Encoder[Task] = deriveEncoder[Task]
}