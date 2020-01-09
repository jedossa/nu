package nu.authorizer.serde

import io.circe.{Decoder, Encoder, Json}
import nu.authorizer.domain.{Account, Currency}
import nu.authorizer.ops.Morph

final case class AccountDto(
  activeCard: Boolean,
  availableLimit: Int,
  violations: List[String]
)

object AccountDto {
  implicit val toDomain: Morph[AccountDto, Account] =
    dto =>
      Account(
        availableLimit = Currency(dto.availableLimit),
        cardStatus = dto.activeCard
      )

  implicit val fromDomain: Morph[Account, AccountDto] =
    account =>
      AccountDto(
        activeCard = account.cardStatus,
        availableLimit = account.availableLimit.value,
        violations = Nil
      )

  implicit val accountDtoEncoder: Encoder[AccountDto] =
    (input: AccountDto) =>
      Json.obj(
        "account" -> Json.obj(
          "active-card" -> Json.fromBoolean(input.activeCard),
          "available-limit" -> Json.fromInt(input.availableLimit)
        ),
        "violations" -> Json.arr(input.violations.map(Json.fromString): _*)
      )

  implicit val accountDtoDecoder: Decoder[AccountDto] = { json =>
    val cursor = json.downField("account")
    for {
      activeCard     <- cursor.downField("active-card").as[Boolean]
      availableLimit <- cursor.downField("available-limit").as[Int]
    } yield AccountDto(activeCard, availableLimit, Nil)
  }
}
