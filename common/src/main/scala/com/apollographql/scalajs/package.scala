package com.apollographql

import scala.scalajs.js

package object scalajs {
  type OptionalValue[+A] = js.UndefOr[A]

  object OptionalValue {
      final val empty: OptionalValue[Nothing] = js.undefined
  }

  type GraphQLQuery = Object

  def gql(operationString: String) = operationString
}
