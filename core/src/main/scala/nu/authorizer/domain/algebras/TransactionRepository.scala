package nu.authorizer.domain.algebras

import nu.authorizer.domain.Transaction

trait TransactionRepository[F[_]] {
  def store(transaction: Transaction): F[Unit]
  def getAll: fs2.Stream[F, Transaction]
}

object TransactionRepository {
  def apply[F[_]: TransactionRepository]: TransactionRepository[F] = implicitly
}
