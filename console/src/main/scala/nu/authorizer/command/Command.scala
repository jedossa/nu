package nu.authorizer.command

import cats.effect._
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import fs2.Stream
import io.circe.Json
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import nu.authorizer.domain._
import nu.authorizer.ops.morphism._
import nu.authorizer.program.modules.LiveService
import nu.authorizer.serde.{AccountDto, TransactionDto}

trait Command[F[_]] {
  def input: Stream[F, String]
  def command: String => F[Unit]
  def execute: Stream[F, Unit] = input evalMap command
}

final class CommandIO[F[_]: Async: Console] private (service: LiveService[F]) extends Command[F] {
  override def input: Stream[F, String] =
    Stream
      .repeatEval(Console[F].readLn.map(Option(_)))
      .map {
        case Some("exit") | None => none
        case Some(line) => line.trim.some
      }
      .unNoneTerminate
      .onComplete(Stream.eval_(Console[F].putStrLn("Exiting...")))

  override def command: String => F[Unit] = line => {
    val console = Console[F]
    import console._, service._
    val operation = parse(line).getOrElse(Json.Null).hcursor.keys.toList.flatten.headOption.getOrElse("")

    operation match {
      case "account" =>
        val account = for {
          dto     <- decode[AccountDto](line)
          account <- dto.to[Account].asRight
        } yield account
        account
          .fold(_.raiseError[F, Account], accountService.create)
          .flatMap(account => putStrLn(account.to[AccountDto].asJson.noSpaces))
          .handleErrorWith { error =>
            transactionService.process.flatMap(account =>
              putStrLn(account.to[AccountDto].copy(violations = List(error.getMessage)).asJson.noSpaces))
          }
      case "transaction" =>
        val transaction = for {
          dto         <- decode[TransactionDto](line)
          transaction <- dto.to[Transaction].asRight
        } yield transaction
        (transaction
          .fold(_.raiseError[F, Unit], transactionService.authorize) >>
          transactionService.process
            .flatMap(account => putStrLn(account.to[AccountDto].asJson.noSpaces))).handleErrorWith {
          case error: AccountNotInitialized =>
            putStrLn(AccountDto(activeCard = false, 0, List(error.getMessage)).asJson.noSpaces)
          case error =>
            transactionService.process.flatMap(account =>
              putStrLn(account.to[AccountDto].copy(violations = List(error.getMessage)).asJson.noSpaces))
        }
      case _ => ().pure
    }
  }
}

object CommandIO {
  def apply[F[_]: Async: Console](service: LiveService[F]): CommandIO[F] = new CommandIO(service)
}
