import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbt.Keys._
import sbtrelease.ReleaseStateTransformations._

name := """mars"""

//scalaVersion in Global := "2.11.8"

val akkaVersion = "2.4.16"

resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/"

libraryDependencies ++= Seq(
//  jdbc,
  cache,
  ws,
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "org.mockito" % "mockito-core" % "2.3.3" % Test,
  "im.actor" % "akka-scalapb-serialization_2.11" % "0.1.14",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.13",
  "com.typesafe.play" %% "play-mailer" % "5.0.0",
  "com.typesafe.play" %% "twirl-compiler" % "1.2.0",
  // Datastores
  "mysql" % "mysql-connector-java" % "5.1.34",
  "com.typesafe.play" %% "play-slick" % "2.0.2",
  "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % "0.3",
  "com.github.etaty" %% "rediscala" % "1.7.0",
  // Amazon SNS SDK
  "com.amazonaws" % "aws-java-sdk" % "1.11.46",
  // Logging
  "net.logstash.logback" % "logstash-logback-encoder" % "4.7",
  // Nexus dependencies
  "me.mig.matter-stream" %% "notification" % "1.0.19",
  "me.mig.matter-stream" %% "reactiveio" % "1.0.19",
  // Gatling
  "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.2" % "test",
  "io.gatling"            % "gatling-test-framework"    % "2.2.2" % "test"
)

dependencyOverrides += "com.trueaccord.lenses" %% "lenses" % "0.4.6"

// Exclude commons-logging since Play has jcl-over-slf4j, which re-implements the logging API.
libraryDependencies ~= { _ map {
  case m if m.organization == "com.typesafe.play" =>
    m.exclude("commons-logging", "commons-logging").
      exclude("com.typesafe.play", "sbt-link")
  case m => m
}}

//=======================================================================================
// Publish
//=======================================================================================
// Publish configurations
publishMavenStyle := true

publishArtifact in Test := false

publishArtifact in (Compile, packageDoc) in ThisBuild := false

publishTo := {
  val nexus = "https://tools.projectgoth.com/nexus/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "content/repositories/releases")
}

// releaseSettings
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  publishArtifacts,
  tagRelease,
  setNextVersion,
  commitNextVersion,
  pushChanges
)

// ==============================================
//  Assembly Settigns
// ==============================================
lazy val jira = SettingKey[String]("jira", "The JIRA issue parameter to be propagated to git commit message.")

lazy val appSecret = Option(System.getProperty("appsecret")).getOrElse("J@O6CmRZgI9q5b;mIklDTh18]EQVitLl85@cPI:y=gX0wQ>QCrq[RqzotyAN0TW8")

lazy val commonSettings = Seq(
//  rpmVendor := "migme",
//  rpmLicense := Some("migme"),
//  version in Rpm := "0.0.6",
//  packageDescription in Rpm := "Notificatoin service of Migme.",

  organization := "me.mig.mars",
  version := (version in ThisBuild).value,
  scalaVersion := "2.11.8",
  jira := sys.props.get("JIRA").getOrElse("QA-XXX"),

  // Disable publishing the scaladoc jar
  sources in (Compile, doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) in ThisBuild := false,

  // Docker settings
  dockerCommands := dockerCommands.value.takeWhile {
    case cmd: Cmd => !cmd.cmd.equals("USER")
    case _ => true
  } ++ Seq(
    ExecCmd("RUN", "apt-get", "update"),
    Cmd("ENV", "DEBIAN_FRONTEND=noninteractive"),
    ExecCmd("RUN", "apt-get", "install", "-y", "postfix"),
    ExecCmd("RUN", "mkdir", "-p", "/usr/local/mars"),
    ExecCmd("RUN", "mkdir", "-p", "/var/log/mars"),
    ExecCmd("RUN", "chown", "-R", "daemon:daemon", "/usr/local/mars"),
    ExecCmd("RUN", "chown", "-R", "daemon:daemon", "/var/log/mars"),
    Cmd("USER", "daemon")
  ) ++ dockerCommands.value.takeRight(2),
  dockerEntrypoint in Docker := Seq("bin/mars", s"-Dplay.crypto.secret='${appSecret}'"),
  dockerRepository := Some("192.168.0.21:5000"), //Some("192.168.0.93:5000"),
  dockerUpdateLatest := true
)

// Gatling testing
lazy val GTest = config("gatling") extend (Test)

lazy val testMars = project
  .in(file("build/test"))
  .enablePlugins(GatlingPlugin)
  .configs(GTest)
  .settings(inConfig(GTest)(Defaults.testSettings): _*)
  .settings(
    scalaSource in GTest := baseDirectory.value / "/test",
    dependencyOverrides += "org.asynchttpclient" % "async-http-client" % "2.0.10" % Test
  )
  .dependsOn(mars)

lazy val mars = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(JavaServerAppPackaging)
  .settings(commonSettings: _*)