package nu.authorizer.domain

import cats.Eq

final case class Currency private (value: Int) extends AnyVal
object Currency {
  implicit val eq: Eq[Currency] = Eq.fromUniversalEquals
}
