package nu.authorizer.infrastructure.repository.rows

import nu.authorizer.domain.{Account, Transaction}
import nu.authorizer.generators.CoreGenerator
import nu.authorizer.infraestructure.repository.rows.{AccountRow, TransactionRow}
import nu.authorizer.ops.morphism._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class RowMorphismTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreGenerator {
  test("Morphs should be reversible") {
    forAll(accountWithActiveCard) { account: Account =>
      assert(account.to[AccountRow].to[Account] == account)
    }

    forAll(accountRowWithInactiveCard) { accountRow: AccountRow =>
      assert(accountRow.to[Account].to[AccountRow] == accountRow)
    }

    forAll(regularTransaction) { transaction: Transaction =>
      assert(transaction.to[TransactionRow].to[Transaction] == transaction)
    }

    forAll(regularTransactionRow) { transactionRow: TransactionRow =>
      assert(transactionRow.to[Transaction].to[TransactionRow] == transactionRow)
    }
  }
}
