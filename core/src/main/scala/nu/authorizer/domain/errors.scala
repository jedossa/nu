package nu.authorizer.domain

final case class ErrorMessage private (message: String) extends AnyVal

sealed abstract class DomainError(val error: ErrorMessage) extends Exception(error.message)

sealed abstract class Violation(override val error: ErrorMessage) extends DomainError(error)

sealed abstract class InvalidInput(override val error: ErrorMessage) extends DomainError(error)

final case class AccountAlreadyInitialized(
  override val error: ErrorMessage = ErrorMessage("account-already-initialized")
) extends Violation(error)

final case class AccountNotInitialized(
  override val error: ErrorMessage = ErrorMessage("account-not-initialized")
) extends Violation(error)

final case class CardNotActive(
  override val error: ErrorMessage = ErrorMessage("card-not-active")
) extends Violation(error)

final case class InsufficientLimit(
  override val error: ErrorMessage = ErrorMessage("insufficient-limit")
) extends Violation(error)

final case class HighFrequencySmallInterval(
  override val error: ErrorMessage = ErrorMessage("high-frequency-small-interval")
) extends Violation(error)

final case class DoubledTransaction(
  override val error: ErrorMessage = ErrorMessage("doubled-transaction")
) extends Violation(error)
