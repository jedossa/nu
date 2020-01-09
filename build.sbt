import com.typesafe.sbt.packager.docker.DockerChmodType
import scoverage.ScoverageKeys._

organization := "nu"
name := "authorizer"

ThisBuild / turbo := true

lazy val commonSettings = Seq(
  scalaVersion := "2.13.1",
  fork in Test := true,
  name := "authorizer",
  daemonUser in Docker := "daemon",
  dockerChmodType := DockerChmodType.UserGroupWriteExecute,
  dockerBaseImage := "openjdk:jre-alpine",
  libraryDependencies ++= Dependencies.common,
  coverageMinimum := 98,
  coverageFailOnMinimum := true,
  coverageHighlighting := true,
  scalafmtOnCompile in ThisBuild := true,
  wartremoverErrors in (Compile, compile) ++= CustomWarts.all,
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

lazy val itSettings = inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings)

lazy val core = project
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    libraryDependencies ++= Dependencies.test,
    name += "-core",
    itSettings
  ).enablePlugins(ScoverageSbtPlugin)

lazy val console =
  project
    .configs(IntegrationTest)
    .settings(
      commonSettings,
      Defaults.itSettings,
      name += "-console",
      libraryDependencies ++= Dependencies.console ++ Dependencies.test,
      coverageExcludedFiles := "<empty>;.*Main.*",
      mainClass in Compile := Option("nu.authorizer.Main"),
      itSettings
    ).dependsOn(core % "compile->compile;test->test")
    .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, ScoverageSbtPlugin)

lazy val root = (project in file("."))
  .aggregate(core, console)
  .settings(name := "root")
  .settings(commonSettings)

addCommandAlias("coverageAgg", ";clean;update;compile;scalafmtCheck;test:scalafmtCheck;coverage;test;it:test;coverageAggregate")
