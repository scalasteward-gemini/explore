package explore.graphql

import io.circe.generic.extras._

package object client {
      type MessageType = String

    implicit val genDevConfig: Configuration =
        Configuration.default.withDiscriminator("type")
}
