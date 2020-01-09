package nu.authorizer.program.interpreters

import java.time.{OffsetDateTime, ZoneId}

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.traverse._
import io.circe.config.parser
import nu.authorizer.domain.algebras.TransactionRepository
import nu.authorizer.domain.{DoubledTransaction, HighFrequencySmallInterval}
import nu.authorizer.generators.CoreGenerator
import nu.authorizer.infraestructure.config.AppConfig
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.TransactionRow
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._

class LiveTransactionValidatorTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreGenerator {
  val config: AppConfig =
    parser.decodePath[AppConfig]("test").getOrElse(AppConfig(interval = 2.minutes, maxTransactions = 3))

  test("There should not be more than 3 transactions on a 2 minutes interval") {
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
    val initialTime: OffsetDateTime = time.sample.getOrElse(OffsetDateTime.now(ZoneId.of("Z")))

    forAll(highFrequencyTransactions(initialTime, config.interval).suchThat(_.size > 3)) {
      case tx :: txs =>
        val program = for {
          xa <- Ref
            .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
            .map(InMemoryTransactor(_, transactionStorage))
          implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xa)
          _ <- txs.traverse(repository.store)
          _ <- LiveTransactionValidator[IO]
            .isTooFrequent(tx, config.interval, config.maxTransactions)
        } yield ()
        assertThrows[HighFrequencySmallInterval](program.unsafeRunSync)
      case _ => succeed
    }

    forAll(Gen.listOf(regularTransaction).suchThat(_.size > 3)) { transactions =>
      val tx :: txs = transactions.distinctBy(_.time.getHour)
      val program = for {
        xa <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xa)
        _ <- txs.traverse(repository.store)
        isTooFrequent <- LiveTransactionValidator[IO]
          .isTooFrequent(tx, config.interval, config.maxTransactions)
      } yield isTooFrequent.isInstanceOf[Unit]
      assert(program.unsafeRunSync)
    }
  }

  test("There should not be more than 1 similar transactions (same amount and merchant) in a 2 minutes interval") {
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
    val initialTime: OffsetDateTime = time.sample.getOrElse(OffsetDateTime.now(ZoneId.of("Z")))

    forAll(doubledTransactions(initialTime, config.interval).suchThat(_.size >= 2)) {
      case tx :: txs =>
        val program = for {
          xa <- Ref
            .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
            .map(InMemoryTransactor(_, transactionStorage))
          implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xa)
          _ <- repository.store(tx)
          _ <- txs.traverse(LiveTransactionValidator[IO].isDoubled(_, config.interval))
        } yield ()
        assertThrows[DoubledTransaction](program.unsafeRunSync)
      case _ => succeed
    }

    forAll(Gen.listOf(regularTransaction).suchThat(_.size >= 2)) { transactions =>
      val tx :: txs = transactions.distinctBy(_.time.getHour)
      val program = for {
        xa <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        implicit0(repository: TransactionRepository[IO]) = MemTransactionRepository[IO](xa)
        _         <- repository.store(tx)
        isDoubled <- txs.traverse(LiveTransactionValidator[IO].isDoubled(_, config.interval))
      } yield isDoubled.isInstanceOf[List[Unit]]
      assert(program.unsafeRunSync)
    }
  }
}
