package nu.authorizer.domain

final case class Account private (
  availableLimit: Currency,
  cardStatus: Boolean
)

object Account {
  def of(availableLimit: Int, cardStatus: Boolean): Account =
    new Account(Currency(availableLimit), cardStatus)
}
