package nu.authorizer.serde

import io.circe.parser.decode
import nu.authorizer.domain.Transaction
import nu.authorizer.generators.CoreItGenerator
import nu.authorizer.ops.morphism._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class TransactionDtoIt extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreItGenerator {
  test("Should morph to domain objects") {
    forAll(regularTransaction) { transaction =>
      assert(transaction.to[Transaction].to[TransactionDto] === transaction)
    }

    forAll(hugeTransaction) { transaction =>
      assert(transaction.to[Transaction].to[TransactionDto] === transaction)
    }
  }

  test("Should parse json as DTO") {
    forAll(regularTransaction) { transaction =>
      val json =
        s"""{"transaction":{"merchant":"${transaction.merchant}","amount":${transaction.amount},"time":"${transaction.time}"}}""".stripMargin
      decode[TransactionDto](json).fold(e => fail(e.getMessage), dto => assert(transaction === dto))
    }

    forAll(hugeTransaction) { transaction =>
      val json =
        s"""{"transaction":{"merchant":"${transaction.merchant}","amount":${transaction.amount},"time":"${transaction.time}"}}""".stripMargin
      decode[TransactionDto](json).fold(e => fail(e.getMessage), dto => assert(transaction === dto))
    }
  }
}
