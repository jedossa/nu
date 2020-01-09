package nu.authorizer.program.interpreters

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.option._
import com.olegpy.meow.hierarchy._
import nu.authorizer.domain._
import nu.authorizer.domain.algebras.{AccountRepository, AccountValidator}
import nu.authorizer.generators.CoreGenerator
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.AccountRow
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class LiveAccountServiceTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreGenerator {
  test("Should create the account") {
    forAll(accountWithActiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        implicit0(validator: AccountValidator[IO]) = LiveAccountValidator[IO]
        created <- LiveAccountService[IO].create(account)
        fetched <- LiveAccountService[IO].getAccount
      } yield account === created && account === fetched
      assert(program.unsafeRunSync)
    }
  }

  test("Once created, the account should not be updated or recreated") {
    forAll(accountWithActiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        implicit0(repository: AccountRepository[IO]) = MemAccountRepository[IO](xa)
        implicit0(validator: AccountValidator[IO]) = LiveAccountValidator[IO]
        _ <- LiveAccountService[IO].create(account)
        _ <- LiveAccountService[IO].create(account)
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
        implicit0(validator: AccountValidator[IO]) = LiveAccountValidator[IO]
        _ <- LiveAccountService[IO].getAccount
      } yield ()
      assertThrows[AccountNotInitialized](program.unsafeRunSync)
    }
  }
}
