package nu.authorizer.program.interpreters

import cats.MonadError
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.functor._
import mouse.boolean._
import nu.authorizer.domain._
import nu.authorizer.domain.algebras.{AccountRepository, AccountValidator}

final class LiveAccountValidator[F[_]: MonadError[*[_], Violation]: AccountRepository] private
  extends AccountValidator[F] {
  override def initialized: F[Unit] =
    AccountRepository[F].fetch.semiflatMap[Unit](_ => AccountAlreadyInitialized().raiseError).getOrElse(())

  override def notInitialized: F[Unit] = AccountRepository[F].fetch.getOrElseF(AccountNotInitialized().raiseError).void

  override def isCardActive(account: Account): F[Unit] = account match {
    case _ if account.cardStatus => ().pure
    case _ => CardNotActive().raiseError
  }

  override def isLimitSufficient(account: Account, transactionAmount: Currency): F[Unit] =
    (transactionAmount.value <= account.availableLimit.value).fold(().pure, InsufficientLimit().raiseError)
}

object LiveAccountValidator {
  def apply[F[_]: MonadError[*[_], Violation]: AccountRepository]: AccountValidator[F] =
    new LiveAccountValidator[F]
}
