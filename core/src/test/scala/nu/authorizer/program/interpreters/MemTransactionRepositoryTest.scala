package nu.authorizer.program.interpreters

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.traverse._
import nu.authorizer.generators.CoreGenerator
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.TransactionRow
import org.scalacheck.Gen
import org.scalatest.funsuite.{AnyFunSuite, AsyncFunSuite}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.immutable.TreeMap

class MemTransactionRepositoryAsyncTest extends AsyncFunSuite with CoreGenerator {
  test("Should get all transactions previously authorized") {
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
    Gen.listOf(regularTransaction).sample.fold(fail()) { transactions =>
      val program = for {
        xa <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = MemTransactionRepository[IO](xa)
        _             <- transactions.traverse(repository.store)
        fetchedValues <- repository.getAll.compile.toList
      } yield fetchedValues.size == transactions.size
      program.unsafeToFuture.map(result => assert(result))
    }
  }
}

class MemTransactionRepositoryTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreGenerator {
  test("Should get a transaction after store it") {
    forAll(regularTransaction) { transaction =>
      val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
      val program = for {
        xa <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = MemTransactionRepository[IO](xa)
        _            <- repository.store(transaction)
        fetchedValue <- repository.getAll.compile.last
      } yield fetchedValue contains transaction
      assert(program.unsafeRunSync)
    }
  }

  test("Get all transactions should be idempotent") {
    forAll(regularTransaction) { transaction =>
      val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
      val program = for {
        xa <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = MemTransactionRepository[IO](xa)
        _            <- repository.store(transaction)
        _            <- repository.getAll.compile.last
        _            <- repository.getAll.compile.last
        fetchedValue <- repository.getAll.compile.last
      } yield fetchedValue contains transaction
      assert(program.unsafeRunSync)
    }
  }

  test("Should be ordered by time") {
    forAll(Gen.listOfN(2, regularTransactionRow), Gen.uuid) { (transactions, uuid) =>
      val sorted = transactions.map(_.time -> uuid).sorted
      val result = for {
        h <- sorted.headOption
        l <- sorted.lastOption
      } yield TransactionRow.policy.gteq(h, l)
      assert(result getOrElse false)
    }
  }
}
