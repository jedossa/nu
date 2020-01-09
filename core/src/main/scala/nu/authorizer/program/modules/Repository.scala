package nu.authorizer.program.modules

import cats.effect.Async
import nu.authorizer.domain.algebras.{AccountRepository, TransactionRepository}
import nu.authorizer.infraestructure.repository.reference.Transactor
import nu.authorizer.infraestructure.repository.rows.{AccountRow, TransactionRow}
import nu.authorizer.program.interpreters.{MemAccountRepository, MemTransactionRepository}

import scala.collection.immutable.TreeMap

trait Repository[F[_]] {
  def accountRepository: AccountRepository[F]
  def transactionRepository: TransactionRepository[F]
}

final class InMemoryRepository[F[_]] private (
  private val accountRepo: AccountRepository[F],
  private val transactionRepo: TransactionRepository[F])
  extends Repository[F] {
  override def accountRepository: AccountRepository[F] = accountRepo

  override def transactionRepository: TransactionRepository[F] = transactionRepo
}

object InMemoryRepository {
  def apply[F[_]: Async](
    accountTransactor: Transactor[F, Option[AccountRow]],
    transactionTransactor: Transactor[F, TreeMap[TransactionRow.Key, TransactionRow]]): InMemoryRepository[F] =
    new InMemoryRepository[F](
      MemAccountRepository[F](accountTransactor),
      MemTransactionRepository[F](transactionTransactor))
}
