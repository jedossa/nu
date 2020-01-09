package nu.authorizer.program.interpreters

import java.time.OffsetDateTime

import cats.effect.Async
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.eq._
import cats.syntax.flatMap._
import mouse.boolean._
import nu.authorizer.domain._
import nu.authorizer.domain.algebras.{TransactionRepository, TransactionValidator}

import scala.concurrent.duration.FiniteDuration

final class LiveTransactionValidator[F[_]: Async: TransactionRepository] private extends TransactionValidator[F] {
  private[this] implicit class DateOps(time: OffsetDateTime) {
    def >=(other: OffsetDateTime): Boolean = time.isAfter(other) || time.isEqual(other)
    def <=(other: OffsetDateTime): Boolean = time.isBefore(other) || time.isEqual(other)
  }

  override def isTooFrequent(transaction: Transaction, duration: FiniteDuration, maxTransactions: Int): F[Unit] =
    TransactionRepository[F].getAll.compile.toList.flatMap { storage =>
      val current = transaction.time
      val windowSize = duration.toMinutes
      val min = current minusMinutes windowSize
      val max = current plusMinutes windowSize

      def window(min: OffsetDateTime, max: OffsetDateTime): Transaction => Boolean =
        tx => tx.time >= min && tx.time <= max
      def inLeftWindow: Transaction => Boolean = window(min, current)
      def inRightWindow: Transaction => Boolean = window(current, max)

      lazy val leftTx = storage count inLeftWindow
      lazy val rightTx = storage count inRightWindow
      lazy val intermediateTx = {
        lazy val inLeftTx = storage
          .find(inLeftWindow)
          .map(tx => tx.time -> tx.time.plusMinutes(windowSize))
          .fold(0)(range => storage.count((window _).tupled(range)))

        lazy val inRightTx = storage.reverse
          .find(inRightWindow)
          .map(tx => tx.time -> tx.time.minusMinutes(windowSize))
          .fold(0)(range => storage.count((window _).tupled(range.swap)))

        (leftTx + rightTx >= maxTransactions).fold(
          if (leftTx > rightTx) inLeftTx
          else if (leftTx < rightTx) inRightTx
          else (inLeftTx >= inRightTx).fold(inLeftTx, inRightTx),
          0)
      }

      (leftTx >= maxTransactions || rightTx >= maxTransactions).fold(
        HighFrequencySmallInterval().raiseError,
        (intermediateTx >= maxTransactions).fold(HighFrequencySmallInterval().raiseError, ().pure))
    }

  override def isDoubled(transaction: Transaction, duration: FiniteDuration): F[Unit] =
    TransactionRepository[F].getAll.compile.toList.flatMap { storage =>
      val current = transaction.time
      val sameTx: Transaction => Boolean =
        tx => tx.merchant === transaction.merchant && tx.amount === transaction.amount
      val min = current minusMinutes duration.toMinutes
      val max = current plusMinutes duration.toMinutes
      lazy val lower =
        storage.exists(transaction => sameTx(transaction) && transaction.time >= min && transaction.time <= current)
      lazy val upper =
        storage.exists(transaction => sameTx(transaction) && transaction.time >= current && transaction.time <= max)
      (lower || upper).fold(DoubledTransaction().raiseError, ().pure)
    }
}

object LiveTransactionValidator {
  def apply[F[_]: Async: TransactionRepository]: LiveTransactionValidator[F] =
    new LiveTransactionValidator[F]
}
