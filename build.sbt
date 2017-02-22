import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbt.Keys._
import sbtrelease.ReleaseStateTransformations._

name := "neith"

//version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  filters,
  "net.kaliber" %% "play-s3" % "8.0.0",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.8",
  // Play test in Scala test
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % "test",

  specs2 % Test
)

unmanagedResourceDirectories in Test +=  baseDirectory ( _ /"target/web/public/test" ).value

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

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
//  Assembly Settings
// ==============================================
lazy val jira = SettingKey[String]("jira", "The JIRA issue parameter to be propagated to git commit message.")
lazy val appSecret = Option(System.getProperty("appsecret")).getOrElse("N@O6CmRZgI9q5b;mIklDTh18]EQVitLl85@cPI:y=gX0wQ>QCrq[RqzotyAN0TW8")

lazy val commonSettings = Seq(
  //rpmVendor := "migme",
  //rpmLicense := Some("migme"),
  //version in Rpm := "0.0.1",
  //packageDescription in Rpm := "Image related service of Migme.",

  organization := "me.mig.neith",
  version := (version in ThisBuild).value,
  scalaVersion := (scalaVersion in ThisBuild).value,//"2.11.8",
  jira := sys.props.get("JIRA").getOrElse("QA-XXX"),

  // Docker settings
  dockerCommands := dockerCommands.value.takeWhile {
    case cmd: Cmd => !cmd.cmd.equals("USER")
    case _ => true
  } ++ Seq(
    ExecCmd("RUN", "mkdir", "-p", "/usr/local/neith"),
    ExecCmd("RUN", "mkdir", "-p", "/var/log/neith"),
    ExecCmd("RUN", "chown", "-R", "daemon:daemon", "/usr/local/neith"),
    ExecCmd("RUN", "chown", "-R", "daemon:daemon", "/var/log/neith"),
    Cmd("USER", "daemon")
  ) ++ dockerCommands.value.takeRight(2),
  dockerEntrypoint in Docker := Seq("bin/neith", s"-Dplay.crypto.secret='${appSecret}'"),
  dockerRepository := Some("192.168.0.21:5000"),//Some("registry.marathon.l4lb.thisdcos.directory:5000"), //Some("192.168.0.123:5000"),//
  dockerUpdateLatest := true
)

lazy val neith = (project in file("."))
  .enablePlugins(PlayScala)
  //must have this for future publish in Jenkins
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings: _*)