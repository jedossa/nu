package nu.authorizer.program.interpreters

import cats.effect.Async
import cats.syntax.apply._
import cats.syntax.flatMap._
import com.softwaremill.quicklens._
import nu.authorizer.domain.algebras._
import nu.authorizer.domain.{Account, Transaction}

import scala.concurrent.duration.FiniteDuration

final class LiveTransactionService[
  F[_]: Async: TransactionRepository: TransactionValidator: AccountService: AccountValidator] private (
  private val duration: FiniteDuration,
  private val maxTransactions: Int)
  extends TransactionService[F] {
  override def authorize(transaction: Transaction): F[Unit] =
    AccountValidator[F].notInitialized *>
      process.flatMap { account =>
        AccountValidator[F].isCardActive(account) *>
          AccountValidator[F].isLimitSufficient(account, transaction.amount)
      } *> TransactionValidator[F].isDoubled(transaction, duration) *>
      TransactionValidator[F].isTooFrequent(transaction, duration, maxTransactions) *>
      TransactionRepository[F].store(transaction)

  override def process: F[Account] = AccountService[F].getAccount flatMap { account =>
    TransactionRepository[F].getAll
      .fold(0)((total, transaction) => total + transaction.amount.value)
      .map(total => account.modify(_.availableLimit.value).using(_ - total))
      .compile
      .lastOrError
  }
}

object LiveTransactionService {
  def apply[F[_]: Async: TransactionRepository: TransactionValidator: AccountService: AccountValidator](
    duration: FiniteDuration,
    maxTransactions: Int): LiveTransactionService[F] = new LiveTransactionService[F](duration, maxTransactions)
}
