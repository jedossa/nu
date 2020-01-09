package nu.authorizer.domain.algebras

import nu.authorizer.domain.Transaction

import scala.concurrent.duration.FiniteDuration

trait TransactionValidator[F[_]] {
  def isTooFrequent(transaction: Transaction, duration: FiniteDuration, maxTransactions: Int): F[Unit]
  def isDoubled(transaction: Transaction, duration: FiniteDuration): F[Unit]
}

object TransactionValidator {
  def apply[F[_]: TransactionValidator]: TransactionValidator[F] = implicitly
}
