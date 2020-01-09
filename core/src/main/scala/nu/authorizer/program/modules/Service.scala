package nu.authorizer.program.modules

import cats.effect.Async
import com.olegpy.meow.hierarchy._
import nu.authorizer.domain.algebras._
import nu.authorizer.infraestructure.config.AppConfig
import nu.authorizer.program.interpreters.{LiveAccountService, LiveTransactionService}

trait Service[F[_]] {
  def accountService: AccountService[F]
  def transactionService: TransactionService[F]
}

final class LiveService[F[_]] private (
  private val accounts: AccountService[F],
  private val transactions: TransactionService[F])
  extends Service[F] {
  override def accountService: AccountService[F] = accounts

  override def transactionService: TransactionService[F] = transactions
}

object LiveService {
  def apply[F[_]: Async](
    repository: Repository[F],
    Validator: Validator[F],
    config: AppConfig
  ): LiveService[F] = {
    implicit val accountRepository: AccountRepository[F] = repository.accountRepository
    implicit val accountValidator: AccountValidator[F] = Validator.accountValidator
    implicit val transactionRepository: TransactionRepository[F] = repository.transactionRepository
    implicit val transactionValidator: TransactionValidator[F] = Validator.transactionValidator
    implicit val accountService: AccountService[F] = LiveAccountService[F]
    new LiveService(accountService, LiveTransactionService[F](config.interval, config.maxTransactions))
  }
}
