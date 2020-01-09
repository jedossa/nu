package nu.authorizer

import cats.effect.Console.implicits._
import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import cats.syntax.option._
import fs2.Stream
import io.circe.config.parser
import nu.authorizer.command.CommandIO
import nu.authorizer.infraestructure.config.AppConfig
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.{AccountRow, TransactionRow}
import nu.authorizer.program.modules.{InMemoryRepository, LiveService, LiveValidator}

import scala.collection.immutable.TreeMap

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Stream.resource(InMemoryApp.make).flatMap(CommandIO(_).execute).compile.drain.as(ExitCode.Success)
}

object InMemoryApp {
  def make[F[_]: ContextShift: ConcurrentEffect: Timer]: Resource[F, LiveService[F]] =
    for {
      config <- Resource.liftF(parser.decodePathF[F, AppConfig]("app"))
      accountStorage: Option[AccountRow] = none[AccountRow]
      accountRef <- Resource.liftF(Ref.of(accountStorage))
      accountTransactor = InMemoryTransactor[F, Option[AccountRow]](accountRef, accountStorage)
      transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
      transactionRef <- Resource.liftF(Ref.of(transactionStorage))
      transactionTransactor = InMemoryTransactor[F, TreeMap[TransactionRow.Key, TransactionRow]](
        transactionRef,
        transactionStorage)
      repository = InMemoryRepository[F](accountTransactor, transactionTransactor)
      validator = LiveValidator[F](repository)
      service = LiveService[F](repository, validator, config)
    } yield service
}
