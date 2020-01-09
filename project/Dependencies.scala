  import sbt._

object Versions {
  lazy val cats = "2.1.0"
  lazy val catsEffects = "2.0.0"
  lazy val fs2 = "2.1.0"
  lazy val circe = "0.12.3"
  lazy val circeConfig = "0.7.0"
  lazy val meowMtl = "0.3.0-M1"
  lazy val mouse = "0.23"
  lazy val lens = "1.4.12"
  lazy val console = "0.8.1"
  lazy val scalaCheck = "1.14.2"
  lazy val scalaTest = "3.1.0"
}

object Dependencies {
  lazy val common: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core"              % Versions.cats withSources () withJavadoc (),
    "org.typelevel" %% "cats-effect"            % Versions.catsEffects withSources () withJavadoc (),
    "co.fs2" %% "fs2-core"                      % Versions.fs2 withSources () withJavadoc (),
    "co.fs2" %% "fs2-io"                        % Versions.fs2 withSources () withJavadoc (),
    "org.typelevel" %% "mouse"                  % Versions.mouse withSources () withJavadoc (),
    "io.circe" %% "circe-core"                  % Versions.circe,
    "io.circe" %% "circe-parser"                % Versions.circe,
    "io.circe" %% "circe-generic"               % Versions.circe,
    "io.circe" %% "circe-config"                % Versions.circeConfig,
    "com.olegpy" %% "meow-mtl"                  % Versions.meowMtl,
    "com.softwaremill.quicklens" %% "quicklens" % Versions.lens
  )

  lazy val console: Seq[ModuleID] = Seq(
    "dev.profunktor" %% "console4cats" % Versions.console
  )

  lazy val test: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest"                    % Versions.scalaTest  % s"it,$Test",
    "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2"       % s"it,$Test",
    "org.scalacheck" %% "scalacheck"                  % Versions.scalaCheck % s"it,$Test"
  )
}
