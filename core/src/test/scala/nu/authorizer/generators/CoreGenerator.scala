package nu.authorizer.generators

import java.time.OffsetDateTime

import nu.authorizer.domain.{Account, Currency, Merchant, Transaction}
import nu.authorizer.infraestructure.repository.rows.{AccountRow, TransactionRow}
import org.scalacheck.Gen

import scala.concurrent.duration.FiniteDuration

trait CoreGenerator extends BaseGenerator {
  def accountWithActiveCard: Gen[Account] =
    for {
      availableLimit <- limit
      cardStatus     <- activeCard
    } yield Account(Currency(availableLimit), cardStatus)

  def accountRowWithActiveCard: Gen[AccountRow] =
    for {
      cardStatus     <- activeCard
      availableLimit <- limit
    } yield AccountRow(cardStatus, availableLimit)

  def accountWithInactiveCard: Gen[Account] =
    for {
      availableLimit <- limit
      cardStatus     <- inactiveCard
    } yield Account(Currency(availableLimit), cardStatus)

  def accountRowWithInactiveCard: Gen[AccountRow] =
    for {
      cardStatus     <- inactiveCard
      availableLimit <- limit
    } yield AccountRow(cardStatus, availableLimit)

  def regularTransaction: Gen[Transaction] =
    for {
      merchant <- merchant
      amount   <- lowAmount
      time     <- time
    } yield Transaction(Merchant(merchant), Currency(amount), time)

  def regularTransactionRow: Gen[TransactionRow] =
    for {
      merchant <- merchant
      amount   <- lowAmount
      time     <- time
    } yield TransactionRow(merchant, amount, time)

  def hugeTransaction: Gen[Transaction] =
    for {
      merchant <- merchant
      amount   <- hugeAmount
      time     <- time
    } yield Transaction(Merchant(merchant), Currency(amount), time)

  def highFrequencyTransactions(initialTime: OffsetDateTime, duration: FiniteDuration): Gen[List[Transaction]] =
    intervalsOf(initialTime, duration).map(_.map { time =>
      val name = merchant.sample.getOrElse("")
      val amount = lowAmount.sample.getOrElse(0)
      Transaction(Merchant(name), Currency(amount), time)
    })

  def doubledTransactions(initialTime: OffsetDateTime, duration: FiniteDuration): Gen[List[Transaction]] =
    for {
      (merchant, amount) <- sameTx
      times              <- intervalsOf(initialTime, duration)
    } yield times.map(Transaction(Merchant(merchant), Currency(amount), _))
}
