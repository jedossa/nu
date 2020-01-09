package nu.authorizer.program.modules

import cats.effect.Async
import com.olegpy.meow.hierarchy._
import nu.authorizer.domain.algebras.{AccountRepository, AccountValidator, TransactionRepository, TransactionValidator}
import nu.authorizer.program.interpreters.{LiveAccountValidator, LiveTransactionValidator}

trait Validator[F[_]] {
  def accountValidator: AccountValidator[F]
  def transactionValidator: TransactionValidator[F]
}

final class LiveValidator[F[_]] private (
  private val accountV: AccountValidator[F],
  private val transactionV: TransactionValidator[F])
  extends Validator[F] {
  override def accountValidator: AccountValidator[F] = accountV

  override def transactionValidator: TransactionValidator[F] = transactionV
}

object LiveValidator {
  def apply[F[_]: Async](repository: Repository[F]): LiveValidator[F] = {
    implicit val accountRepository: AccountRepository[F] = repository.accountRepository
    implicit val transactionRepository: TransactionRepository[F] = repository.transactionRepository
    new LiveValidator[F](LiveAccountValidator[F], LiveTransactionValidator[F])
  }
}
