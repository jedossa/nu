package nu.authorizer.program.interpreters

import cats.MonadError
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import nu.authorizer.domain.algebras.{AccountRepository, AccountService, AccountValidator}
import nu.authorizer.domain.{Account, AccountNotInitialized, DomainError}

final class LiveAccountService[F[_]: MonadError[*[_], DomainError]: AccountRepository: AccountValidator] private
  extends AccountService[F] {
  override def create(account: Account): F[Account] =
    AccountValidator[F].initialized *>
      AccountRepository[F].create(account) *>
      account.pure

  override def getAccount: F[Account] =
    AccountRepository[F].fetch.getOrElseF(AccountNotInitialized().raiseError)
}

object LiveAccountService {
  def apply[F[_]: MonadError[*[_], DomainError]: AccountRepository: AccountValidator]: AccountService[F] =
    new LiveAccountService[F]
}
