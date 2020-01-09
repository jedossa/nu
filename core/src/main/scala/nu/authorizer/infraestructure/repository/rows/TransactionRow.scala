package nu.authorizer.infraestructure.repository.rows

import java.time.OffsetDateTime
import java.util.UUID

import nu.authorizer.domain._
import nu.authorizer.ops.Morph

final case class TransactionRow private (merchant: String, amount: Int, time: OffsetDateTime)

object TransactionRow {
  type Key = (OffsetDateTime, UUID)

  implicit val policy: Ordering[TransactionRow.Key] =
    Ordering.fromLessThan[TransactionRow.Key]((t, o) => t._1.isAfter(o._1) || t._2.compareTo(o._2) < 0)

  implicit def toDomain: Morph[TransactionRow, Transaction] =
    row => Transaction.of(merchant = row.merchant, amount = row.amount, time = row.time)

  implicit def fromDomain: Morph[Transaction, TransactionRow] =
    transaction =>
      TransactionRow(merchant = transaction.merchant.name, amount = transaction.amount.value, time = transaction.time)
}
