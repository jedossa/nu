package nu.authorizer.program.interpreters

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.option._
import com.olegpy.meow.hierarchy._
import nu.authorizer.domain._
import nu.authorizer.domain.algebras.AccountRepository
import nu.authorizer.generators.CoreGenerator
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.AccountRow
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class LiveAccountValidatorTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreGenerator {
  test("Should validate if the account is initialized") {
    forAll(accountWithActiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        initialized    <- LiveAccountValidator[IO].initialized
        _              <- repository.create(account)
        notInitialized <- LiveAccountValidator[IO].notInitialized
      } yield initialized.isInstanceOf[Unit] && notInitialized.isInstanceOf[Unit]
      assert(program.unsafeRunSync)
    }
  }

  test("Once created, the account should not be updated or recreated") {
    forAll(accountWithActiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        _ <- repository.create(account)
        _ <- LiveAccountValidator[IO].initialized
      } yield ()
      assertThrows[AccountAlreadyInitialized](program.unsafeRunSync)
    }
  }

  test("Should fail if account is not initialized") {
    forAll(accountWithActiveCard) { _ =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        _ <- LiveAccountValidator[IO].notInitialized
      } yield ()
      assertThrows[AccountNotInitialized](program.unsafeRunSync)
    }
  }

  test("Should validate if the account's card is active or not") {
    forAll(accountWithActiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        cardStatus <- LiveAccountValidator[IO].isCardActive(account).map(_ => account.cardStatus)
      } yield cardStatus
      assert(program.unsafeRunSync)
    }

    forAll(accountWithInactiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        cardStatus <- LiveAccountValidator[IO].isCardActive(account).map(_ => account.cardStatus)
      } yield cardStatus
      assertThrows[CardNotActive](program.unsafeRunSync)
    }
  }

  test("Should validate if the account's available limit is sufficient for a given amount") {
    forAll(accountWithActiveCard, lowAmount) { (account, amount) =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        valid <- LiveAccountValidator[IO].isLimitSufficient(account, Currency(amount))
      } yield valid.isInstanceOf[Unit]
      assert(program.unsafeRunSync)
    }

    forAll(accountWithActiveCard, hugeAmount) { (account, amount) =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        _ <- LiveAccountValidator[IO].isLimitSufficient(account, Currency(amount))
      } yield ()
      assertThrows[InsufficientLimit](program.unsafeRunSync)
    }
  }
}
