package nu.authorizer.domain.algebras

import nu.authorizer.domain.Account

trait AccountService[F[_]] {
  def create(account: Account): F[Account]
  def getAccount: F[Account]
}

object AccountService {
  def apply[F[_]: AccountService]: AccountService[F] = implicitly
}
