package nu.authorizer.program.interpreters

import java.time.{OffsetDateTime, ZoneId}

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.option.none
import cats.syntax.traverse._
import com.olegpy.meow.hierarchy._
import io.circe.config.parser
import nu.authorizer.domain._
import nu.authorizer.domain.algebras._
import nu.authorizer.generators.CoreGenerator
import nu.authorizer.infraestructure.config.AppConfig
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.{AccountRow, TransactionRow}
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._

class LiveTransactionServiceTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreGenerator {
  val config: AppConfig =
    parser.decodePath[AppConfig]("test").getOrElse(AppConfig(interval = 2.minutes, maxTransactions = 3))

  test("Should get the account's current state") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(accountWithActiveCard, Gen.listOf(regularTransaction)) { (account, transactions) =>
      val txs = transactions.distinctBy(_.time.getHour)
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        implicit0(accountRepository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        implicit0(accountValidator: AccountValidator[IO]) = LiveAccountValidator[IO]
        implicit0(accountService: AccountService[IO]) = LiveAccountService[IO]
        implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xt)
        implicit0(validator: TransactionValidator[IO]) = LiveTransactionValidator[IO]
        _ <- LiveAccountService[IO].create(account)
        service = LiveTransactionService[IO](config.interval, config.maxTransactions)
        _            <- txs.traverse(service.authorize)
        currentState <- service.process
      } yield currentState.availableLimit.value == account.availableLimit.value - txs.foldLeft(0)((total, tx) =>
        total + tx.amount.value)
      assert(program.unsafeRunSync)
    }
  }

  test("Should fail if the account is not initialized") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(Gen.listOf(regularTransaction).suchThat(_.nonEmpty)) { transactions =>
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        implicit0(accountRepository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        implicit0(accountValidator: AccountValidator[IO]) = LiveAccountValidator[IO]
        implicit0(accountService: AccountService[IO]) = LiveAccountService[IO]
        implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xt)
        implicit0(validator: TransactionValidator[IO]) = LiveTransactionValidator[IO]
        service = LiveTransactionService[IO](config.interval, config.maxTransactions)
        _ <- transactions.traverse(service.authorize)
      } yield ()
      assertThrows[AccountNotInitialized](program.unsafeRunSync)
    }
  }

  test("Should fail if the account's card is not active") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(accountWithInactiveCard, Gen.listOf(regularTransaction).suchThat(_.nonEmpty)) { (account, transactions) =>
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        implicit0(accountRepository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        implicit0(accountValidator: AccountValidator[IO]) = LiveAccountValidator[IO]
        implicit0(accountService: AccountService[IO]) = LiveAccountService[IO]
        implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xt)
        implicit0(validator: TransactionValidator[IO]) = LiveTransactionValidator[IO]
        _ <- LiveAccountService[IO].create(account)
        service = LiveTransactionService[IO](config.interval, config.maxTransactions)
        _ <- transactions.traverse(service.authorize)
      } yield ()
      assertThrows[CardNotActive](program.unsafeRunSync)
    }
  }

  test("Should fail if the account's available limit is not sufficient") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(accountWithActiveCard, hugeTransaction) { (account, transaction) =>
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        implicit0(accountRepository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        implicit0(accountValidator: AccountValidator[IO]) = LiveAccountValidator[IO]
        implicit0(accountService: AccountService[IO]) = LiveAccountService[IO]
        implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xt)
        implicit0(validator: TransactionValidator[IO]) = LiveTransactionValidator[IO]
        _ <- LiveAccountService[IO].create(account)
        service = LiveTransactionService[IO](config.interval, config.maxTransactions)
        _ <- service.authorize(transaction)
      } yield ()
      assertThrows[InsufficientLimit](program.unsafeRunSync)
    }
  }

  test("There should not be more than 3 transactions on a 2 minutes interval") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
    val initialTime: OffsetDateTime = time.sample.getOrElse(OffsetDateTime.now(ZoneId.of("Z")))

    forAll(accountWithActiveCard, highFrequencyTransactions(initialTime, config.interval).suchThat(_.size > 3)) {
      (account, transactions) =>
        val program = for {
          xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
          xt <- Ref
            .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
            .map(InMemoryTransactor(_, transactionStorage))
          implicit0(accountRepository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
          implicit0(accountValidator: AccountValidator[IO]) = LiveAccountValidator[IO]
          implicit0(accountService: AccountService[IO]) = LiveAccountService[IO]
          implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xt)
          implicit0(validator: TransactionValidator[IO]) = LiveTransactionValidator[IO]
          _ <- LiveAccountService[IO].create(account)
          service = LiveTransactionService[IO](config.interval, config.maxTransactions)
          _ <- transactions.traverse(service.authorize)
        } yield ()
        assertThrows[HighFrequencySmallInterval](program.unsafeRunSync)
    }
  }

  test("There should not be more than 1 similar transactions (same amount and merchant) in a 2 minutes interval") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
    val initialTime: OffsetDateTime = time.sample.getOrElse(OffsetDateTime.now(ZoneId.of("Z")))

    forAll(accountWithActiveCard, doubledTransactions(initialTime, config.interval).suchThat(_.size > 1)) {
      (account, transactions) =>
        val program = for {
          xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
          xt <- Ref
            .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
            .map(InMemoryTransactor(_, transactionStorage))
          implicit0(accountRepository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
          implicit0(accountValidator: AccountValidator[IO]) = LiveAccountValidator[IO]
          implicit0(accountService: AccountService[IO]) = LiveAccountService[IO]
          implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xt)
          implicit0(validator: TransactionValidator[IO]) = LiveTransactionValidator[IO]
          _ <- LiveAccountService[IO].create(account)
          service = LiveTransactionService[IO](config.interval, config.maxTransactions)
          _ <- transactions.traverse(service.authorize)
        } yield ()
        assertThrows[DoubledTransaction](program.unsafeRunSync)
    }
  }
}
