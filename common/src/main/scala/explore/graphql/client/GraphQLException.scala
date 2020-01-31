package explore.graphql.client

import io.circe.Json

class GraphQLException(val errors: List[Json]) extends Exception(errors.toString)
