package nu.authorizer.domain

import java.time.OffsetDateTime

import cats.Eq

final case class Transaction private (
  merchant: Merchant,
  amount: Currency,
  time: OffsetDateTime
)

object Transaction {
  def of(merchant: String, amount: Int, time: OffsetDateTime): Transaction =
    new Transaction(Merchant(merchant), Currency(amount), time)
}

final case class Merchant private (name: String) extends AnyVal
object Merchant {
  implicit val eq: Eq[Merchant] = Eq.fromUniversalEquals
}
