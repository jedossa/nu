package nu.authorizer.infraestructure.config

import io.circe.Decoder
import io.circe.config.syntax.durationDecoder
import io.circe.generic.semiauto.deriveDecoder

import scala.concurrent.duration.FiniteDuration

final case class AppConfig(interval: FiniteDuration, maxTransactions: Int)

object AppConfig {
  implicit val decoder: Decoder[AppConfig] = deriveDecoder[AppConfig]
}
