package nu.authorizer.domain.algebras

import nu.authorizer.domain.{Account, Currency}

trait AccountValidator[F[_]] {
  def initialized: F[Unit]
  def notInitialized: F[Unit]
  def isCardActive(account: Account): F[Unit]
  def isLimitSufficient(account: Account, transactionAmount: Currency): F[Unit]
}

object AccountValidator {
  def apply[F[_]: AccountValidator]: AccountValidator[F] =
    implicitly
}
