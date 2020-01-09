package nu.authorizer.domain.algebras

import cats.data.OptionT
import nu.authorizer.domain.Account

trait AccountRepository[F[_]] {
  def create(account: Account): F[Unit]
  def fetch: OptionT[F, Account]
}

object AccountRepository {
  def apply[F[_]: AccountRepository]: AccountRepository[F] =
    implicitly
}
