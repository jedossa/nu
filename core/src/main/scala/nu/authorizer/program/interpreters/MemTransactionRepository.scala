package nu.authorizer.program.interpreters

import java.util.UUID

import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import nu.authorizer.domain.Transaction
import nu.authorizer.domain.algebras.TransactionRepository
import nu.authorizer.infraestructure.repository.reference.Transactor
import nu.authorizer.infraestructure.repository.rows.TransactionRow
import nu.authorizer.ops.morphism._

import scala.collection.immutable.TreeMap

final class MemTransactionRepository[F[_]: Async] private (
  private val xa: Transactor[F, TreeMap[TransactionRow.Key, TransactionRow]]
) extends TransactionRepository[F] {
  override def store(transaction: Transaction): F[Unit] =
    for {
      storage <- xa.ref.get
      row = (transaction.time, UUID.randomUUID) -> transaction.to[TransactionRow]
      next <- xa.ref.set(storage + row)
    } yield next

  override def getAll: Stream[F, Transaction] = Stream.evalSeq(xa.ref.get.map(_.map(_._2.to[Transaction]).toList))
}

object MemTransactionRepository {
  def apply[F[_]: Async](xa: Transactor[F, TreeMap[TransactionRow.Key, TransactionRow]]): MemTransactionRepository[F] =
    new MemTransactionRepository(xa)
}
