package nu.authorizer.generators

import java.time._

import org.scalacheck.Gen

import scala.concurrent.duration.FiniteDuration

trait BaseItGenerator {
  val activeCard: Gen[Boolean] = Gen.const(true)
  val inactiveCard: Gen[Boolean] = Gen.const(false)
  val limit: Gen[Int] = Gen.chooseNum(10000, 1000000)
  val merchant: Gen[String] = Gen.alphaStr
  val lowAmount: Gen[Int] = Gen.chooseNum(0, 100)
  val hugeAmount: Gen[Int] = Gen.const(10000001)
  val sameTx: Gen[(String, Int)] = Gen.const("Burger King" -> 10)
  val zone: ZoneId = ZoneId.of("Z")
  val offset: ZoneOffset = ZoneOffset.of("Z")
  val violations: Gen[String] = Gen.oneOf(
    "account-already-initialized",
    "account-not-initialized",
    "card-not-active",
    "insufficient-limit",
    "high-frequency-small-interval",
    "doubled-transaction")

  val time: Gen[OffsetDateTime] = for {
    hour <- Gen.chooseNum(0, 23)
    min  <- Gen.chooseNum(0, 59)
    rangeStart = LocalDateTime.of(2019, 1, 1, hour, min)
    rangeEnd = LocalDateTime.now(zone)
    epoch <- Gen.chooseNum(rangeStart.toEpochSecond(offset), rangeEnd.toEpochSecond(offset))
  } yield OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), zone)

  def intervalsOf(initialTime: OffsetDateTime, duration: FiniteDuration): Gen[List[OffsetDateTime]] = {
    val minimum = initialTime.minusMinutes(duration.toMinutes)
    val maximum = initialTime.plusMinutes(duration.toMinutes)
    val (min, max) =
      Gen
        .oneOf(minimum.toEpochSecond -> initialTime.toEpochSecond, initialTime.toEpochSecond -> maximum.toEpochSecond)
        .sample
        .getOrElse(
          minimum.toEpochSecond -> initialTime.toEpochSecond
        )
    Gen.listOf(Gen.chooseNum(min, max).map(epoch => OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), zone)))
  }
}
