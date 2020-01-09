package nu.authorizer.program.interpreters

import cats.data.OptionT
import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.option._
import nu.authorizer.domain.Account
import nu.authorizer.domain.algebras.AccountRepository
import nu.authorizer.infraestructure.repository.reference.Transactor
import nu.authorizer.infraestructure.repository.rows.AccountRow
import nu.authorizer.ops.morphism._

final class MemAccountRepository[F[_]: Async] private (
  private val xa: Transactor[F, Option[AccountRow]]
) extends AccountRepository[F] {
  override def create(account: Account): F[Unit] = xa.ref.set(account.to[AccountRow].some)

  override def fetch: OptionT[F, Account] = OptionT(xa.ref.get.map(_.map(_.to[Account])))
}

object MemAccountRepository {
  def apply[F[_]: Async](xa: Transactor[F, Option[AccountRow]]): AccountRepository[F] =
    new MemAccountRepository(xa)
}
