package nu.authorizer.infraestructure.repository.rows

import nu.authorizer.domain._
import nu.authorizer.ops.Morph

final case class AccountRow private (
  cardStatus: Boolean,
  availableLimit: Int
)

object AccountRow {
  implicit def toDomain: Morph[AccountRow, Account] =
    row => Account.of(availableLimit = row.availableLimit, cardStatus = row.cardStatus)

  implicit def fromDomain: Morph[Account, AccountRow] =
    account => new AccountRow(cardStatus = account.cardStatus, availableLimit = account.availableLimit.value)
}
