package nu.authorizer.command

import java.time.{OffsetDateTime, ZoneId}

import cats.data.Chain
import cats.effect.concurrent.Ref
import cats.effect.test.TestConsole
import cats.effect.{Console, IO}
import cats.syntax.option.none
import io.circe.config.parser
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.circe._, io.circe.parser._
import nu.authorizer.domain._
import nu.authorizer.generators.CoreItGenerator
import nu.authorizer.infraestructure.config.AppConfig
import nu.authorizer.infraestructure.repository.reference.InMemoryTransactor
import nu.authorizer.infraestructure.repository.rows.{AccountRow, TransactionRow}
import nu.authorizer.program.modules.{InMemoryRepository, LiveService, LiveValidator}
import nu.authorizer.serde.{AccountDto, TransactionDto}
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._

class CommandIt extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CoreItGenerator {
  val config: AppConfig =
    parser.decodePath[AppConfig]("test").getOrElse(AppConfig(interval = 2.minutes, maxTransactions = 3))

  implicit val encoder: Encoder[TransactionDto] =
    (input: TransactionDto) =>
      Json.obj(
        "transaction" -> Json.obj(
          "merchant" -> Json.fromString(input.merchant),
          "amount" -> Json.fromInt(input.amount),
          "time" -> input.time.asJson
        ))

  implicit val accountDtoDecoder: Decoder[AccountDto] = { json =>
    val cursor = json.downField("account")
    for {
      activeCard     <- cursor.downField("active-card").as[Boolean]
      availableLimit <- cursor.downField("available-limit").as[Int]
      violations     <- json.downField("violations").as[List[String]]
    } yield AccountDto(activeCard, availableLimit, violations)
  }

  test("Should get the account's current state") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(accountWithActiveCard, Gen.listOf(regularTransaction)) { (account, transactions) =>
      val txs = transactions.distinctBy(_.time.getHour)
      val program = for {
        outLines  <- Ref[IO].of(Chain.empty[String])
        outWords  <- Ref[IO].of(Chain.empty[String])
        outErrors <- Ref[IO].of(Chain.empty[String])
        xa        <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = InMemoryRepository[IO](xa, xt)
        validator = LiveValidator[IO](repository)
        service = LiveService[IO](repository, validator, config)
        inputs <- TestConsole.inputs
          .sequenceAndDefault[IO](
            Chain.one(account.asJson.noSpaces) ++ Chain.fromSeq(txs.map(_.asJson.noSpaces)),
            "exit")
        implicit0(console: Console[IO]) = TestConsole.make(outLines, outWords, outErrors, inputs)
        _       <- CommandIO[IO](service).execute.compile.drain
        outputs <- outLines.get
      } yield outputs.initLast
        .flatMap(_._1.lastOption.flatMap(decode[AccountDto](_).toOption))
        .fold(0)(_.availableLimit) == account.availableLimit - txs.foldLeft(0)((total, tx) => total + tx.amount)

      assert(program.unsafeRunSync)
    }
  }

  test("Once created, the account should not be updated or recreated") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(accountWithActiveCard) { account =>
      val program = for {
        outLines  <- Ref[IO].of(Chain.empty[String])
        outWords  <- Ref[IO].of(Chain.empty[String])
        outErrors <- Ref[IO].of(Chain.empty[String])
        xa        <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = InMemoryRepository[IO](xa, xt)
        validator = LiveValidator[IO](repository)
        service = LiveService[IO](repository, validator, config)
        inputs <- TestConsole.inputs
          .sequenceAndDefault[IO](Chain.one(account.asJson.noSpaces) ++ Chain.one(account.asJson.noSpaces), "exit")
        implicit0(console: Console[IO]) = TestConsole.make(outLines, outWords, outErrors, inputs)
        _       <- CommandIO[IO](service).execute.compile.drain
        outputs <- outLines.get
      } yield outputs.initLast
        .flatMap(_._1.lastOption.flatMap(decode[AccountDto](_).toOption))
        .exists(_.violations == List(AccountAlreadyInitialized().error.message))

      assert(program.unsafeRunSync)
    }
  }

  test("No transaction should be accepted without a properly initialized account") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(Gen.listOf(regularTransaction).suchThat(_.nonEmpty)) { transactions =>
      val txs = transactions.distinctBy(_.time.getHour)
      val program = for {
        outLines  <- Ref[IO].of(Chain.empty[String])
        outWords  <- Ref[IO].of(Chain.empty[String])
        outErrors <- Ref[IO].of(Chain.empty[String])
        xa        <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = InMemoryRepository[IO](xa, xt)
        validator = LiveValidator[IO](repository)
        service = LiveService[IO](repository, validator, config)
        inputs <- TestConsole.inputs
          .sequenceAndDefault[IO](Chain.fromSeq(txs.map(_.asJson.noSpaces)), "exit")
        implicit0(console: Console[IO]) = TestConsole.make(outLines, outWords, outErrors, inputs)
        _       <- CommandIO[IO](service).execute.compile.drain
        outputs <- outLines.get
      } yield outputs.initLast
        .flatMap(_._1.lastOption.flatMap(decode[AccountDto](_).toOption))
        .exists(_.violations == List(AccountNotInitialized().error.message))

      assert(program.unsafeRunSync)
    }
  }

  test("No transaction should be accepted when the card is not active") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(accountWithInactiveCard, Gen.listOf(regularTransaction).suchThat(_.nonEmpty)) { (account, transactions) =>
      val txs = transactions.distinctBy(_.time.getHour)
      val program = for {
        outLines  <- Ref[IO].of(Chain.empty[String])
        outWords  <- Ref[IO].of(Chain.empty[String])
        outErrors <- Ref[IO].of(Chain.empty[String])
        xa        <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = InMemoryRepository[IO](xa, xt)
        validator = LiveValidator[IO](repository)
        service = LiveService[IO](repository, validator, config)
        inputs <- TestConsole.inputs
          .sequenceAndDefault[IO](
            Chain.one(account.asJson.noSpaces) ++ Chain.fromSeq(txs.map(_.asJson.noSpaces)),
            "exit")
        implicit0(console: Console[IO]) = TestConsole.make(outLines, outWords, outErrors, inputs)
        _       <- CommandIO[IO](service).execute.compile.drain
        outputs <- outLines.get
      } yield outputs.initLast
        .flatMap(_._1.lastOption.flatMap(decode[AccountDto](_).toOption))
        .exists(_.violations == List(CardNotActive().error.message))

      assert(program.unsafeRunSync)
    }
  }

  test("The transaction amount should not exceed the available limit") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]

    forAll(accountWithActiveCard, hugeTransaction) { (account, transaction) =>
      val program = for {
        outLines  <- Ref[IO].of(Chain.empty[String])
        outWords  <- Ref[IO].of(Chain.empty[String])
        outErrors <- Ref[IO].of(Chain.empty[String])
        xa        <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
        xt <- Ref
          .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
          .map(InMemoryTransactor(_, transactionStorage))
        repository = InMemoryRepository[IO](xa, xt)
        validator = LiveValidator[IO](repository)
        service = LiveService[IO](repository, validator, config)
        inputs <- TestConsole.inputs
          .sequenceAndDefault[IO](Chain.one(account.asJson.noSpaces) ++ Chain.one(transaction.asJson.noSpaces), "exit")
        implicit0(console: Console[IO]) = TestConsole.make(outLines, outWords, outErrors, inputs)
        _       <- CommandIO[IO](service).execute.compile.drain
        outputs <- outLines.get
      } yield outputs.initLast
        .flatMap(_._1.lastOption.flatMap(decode[AccountDto](_).toOption))
        .exists(_.violations == List(InsufficientLimit().error.message))

      assert(program.unsafeRunSync)
    }
  }

  test("There should not be more than 3 transactions on a 2-minute interval") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
    val initialTime: OffsetDateTime = time.sample.getOrElse(OffsetDateTime.now(ZoneId.of("Z")))

    forAll(accountWithActiveCard, highFrequencyTransactions(initialTime, config.interval).suchThat(_.size > 3)) {
      (account, transactions) =>
        val program = for {
          outLines  <- Ref[IO].of(Chain.empty[String])
          outWords  <- Ref[IO].of(Chain.empty[String])
          outErrors <- Ref[IO].of(Chain.empty[String])
          xa        <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
          xt <- Ref
            .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
            .map(InMemoryTransactor(_, transactionStorage))
          repository = InMemoryRepository[IO](xa, xt)
          validator = LiveValidator[IO](repository)
          service = LiveService[IO](repository, validator, config)
          inputs <- TestConsole.inputs
            .sequenceAndDefault[IO](
              Chain.one(account.asJson.noSpaces) ++ Chain.fromSeq(transactions.map(_.asJson.noSpaces)),
              "exit")
          implicit0(console: Console[IO]) = TestConsole.make(outLines, outWords, outErrors, inputs)
          _       <- CommandIO[IO](service).execute.compile.drain
          outputs <- outLines.get
        } yield outputs.initLast
          .flatMap(_._1.lastOption.flatMap(decode[AccountDto](_).toOption))
          .exists(_.violations == List(HighFrequencySmallInterval().error.message))

        assert(program.unsafeRunSync)
    }
  }

  test("There should not be more than 1 similar transactions (same amount and merchant) in a 2 minutes interval") {
    val accountStorage: Option[AccountRow] = none[AccountRow]
    val transactionStorage = TreeMap.empty[TransactionRow.Key, TransactionRow]
    val initialTime: OffsetDateTime = time.sample.getOrElse(OffsetDateTime.now(ZoneId.of("Z")))

    forAll(accountWithActiveCard, doubledTransactions(initialTime, config.interval).suchThat(_.size > 1)) {
      (account, transactions) =>
        val program = for {
          outLines  <- Ref[IO].of(Chain.empty[String])
          outWords  <- Ref[IO].of(Chain.empty[String])
          outErrors <- Ref[IO].of(Chain.empty[String])
          xa        <- Ref.of[IO, Option[AccountRow]](accountStorage).map(InMemoryTransactor(_, accountStorage))
          xt <- Ref
            .of[IO, TreeMap[TransactionRow.Key, TransactionRow]](transactionStorage)
            .map(InMemoryTransactor(_, transactionStorage))
          repository = InMemoryRepository[IO](xa, xt)
          validator = LiveValidator[IO](repository)
          service = LiveService[IO](repository, validator, config)
          inputs <- TestConsole.inputs
            .sequenceAndDefault[IO](
              Chain.one(account.asJson.noSpaces) ++ Chain.fromSeq(transactions.map(_.asJson.noSpaces)),
              "exit")
          implicit0(console: Console[IO]) = TestConsole.make(outLines, outWords, outErrors, inputs)
          _       <- CommandIO[IO](service).execute.compile.drain
          outputs <- outLines.get
        } yield outputs.initLast
          .flatMap(_._1.lastOption.flatMap(decode[AccountDto](_).toOption))
          .exists(_.violations == List(DoubledTransaction().error.message))

        assert(program.unsafeRunSync)
    }
  }
}
