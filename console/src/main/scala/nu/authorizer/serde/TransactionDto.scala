package nu.authorizer.serde

import java.time.OffsetDateTime

import io.circe.Decoder
import nu.authorizer.domain.{Currency, Merchant, Transaction}
import nu.authorizer.ops.Morph

final case class TransactionDto(
  merchant: String,
  amount: Int,
  time: OffsetDateTime
)

object TransactionDto {
  implicit val toDomain: Morph[TransactionDto, Transaction] =
    dto =>
      Transaction(
        merchant = Merchant(dto.merchant),
        amount = Currency(dto.amount),
        time = dto.time
      )

  implicit val fromDomain: Morph[Transaction, TransactionDto] =
    transaction =>
      TransactionDto(
        merchant = transaction.merchant.name,
        amount = transaction.amount.value,
        time = transaction.time
      )

  implicit val transactionDtoDecoder: Decoder[TransactionDto] = { json =>
    val cursor = json.downField("transaction")
    for {
      merchant <- cursor.downField("merchant").as[String]
      amount   <- cursor.downField("amount").as[Int]
      time     <- cursor.downField("time").as[OffsetDateTime]
    } yield TransactionDto(merchant, amount, time)
  }

}
