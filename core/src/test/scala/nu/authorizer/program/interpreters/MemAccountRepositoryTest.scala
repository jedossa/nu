package nu.authorizer.program.interpreters

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.option._
import nu.authorizer.generators.CoreGenerator
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.AccountRow
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class MemAccountRepositoryTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreGenerator {
  test("Should get the account after create it") {
    forAll(accountWithActiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        repository = MemAccountRepository[IO](xa)
        _            <- repository.create(account)
        fetchedValue <- repository.fetch.value
      } yield fetchedValue contains account
      assert(program.unsafeRunSync)
    }
  }

  test("Fetch should be idempotent") {
    forAll(accountWithActiveCard) { account =>
      val accountStorage: Option[AccountRow] = none[AccountRow]
      val program = for {
        xa <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        repository = MemAccountRepository[IO](xa)
        _            <- repository.create(account)
        _            <- repository.fetch.value
        _            <- repository.fetch.value
        fetchedValue <- repository.fetch.value
      } yield fetchedValue contains account
      assert(program.unsafeRunSync)
    }
  }
}
