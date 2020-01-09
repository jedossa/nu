package nu.authorizer.domain.algebras

import nu.authorizer.domain.{Account, Transaction}

trait TransactionService[F[_]] {
  def authorize(transaction: Transaction): F[Unit]
  def process: F[Account]
}
