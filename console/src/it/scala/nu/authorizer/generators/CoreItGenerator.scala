package nu.authorizer.generators

import java.time.OffsetDateTime

import nu.authorizer.serde.{AccountDto, TransactionDto}
import org.scalacheck.Gen

import scala.concurrent.duration.FiniteDuration

trait CoreItGenerator extends BaseItGenerator {
  def accountWithActiveCard: Gen[AccountDto] =
    for {
      availableLimit <- limit
      cardStatus     <- activeCard
    } yield AccountDto(cardStatus, availableLimit, Nil)

  def accountWithInactiveCard: Gen[AccountDto] =
    for {
      availableLimit <- limit
      cardStatus     <- inactiveCard
    } yield AccountDto(cardStatus, availableLimit, Nil)

  def accountWithViolations: Gen[AccountDto] =
    for {
      availableLimit <- limit
      cardStatus     <- activeCard
      violations     <- violations
    } yield AccountDto(cardStatus, availableLimit, violations :: Nil)

  def regularTransaction: Gen[TransactionDto] =
    for {
      merchant <- merchant
      amount   <- lowAmount
      time     <- time
    } yield TransactionDto(merchant, amount, time)

  def hugeTransaction: Gen[TransactionDto] =
    for {
      merchant <- merchant
      amount   <- hugeAmount
      time     <- time
    } yield TransactionDto(merchant, amount, time)

  def highFrequencyTransactions(initialTime: OffsetDateTime, duration: FiniteDuration): Gen[List[TransactionDto]] =
    intervalsOf(initialTime, duration).map(_.map { time =>
      val name = merchant.sample.getOrElse("")
      val amount = lowAmount.sample.getOrElse(0)
      TransactionDto(name, amount, time)
    })

  def doubledTransactions(initialTime: OffsetDateTime, duration: FiniteDuration): Gen[List[TransactionDto]] =
    for {
      (merchant, amount) <- sameTx
      times              <- intervalsOf(initialTime, duration)
    } yield times.map(TransactionDto(merchant, amount, _))
}
