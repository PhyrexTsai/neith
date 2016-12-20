import sbt.Attributed
import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy
import sbtrelease.ReleaseStateTransformations._

name := """mars"""

scalaVersion in Global := "2.11.8"

val akkaVersion = "2.4.14"

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
  "me.mig.matter-stream" %% "notification" % "1.0.14",
  "me.mig.matter-stream" %% "reactiveio" % "1.0.14",
  // Gatling
  "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.2" % "test",
  "io.gatling"            % "gatling-test-framework"    % "2.2.2" % "test"
)

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

lazy val commonSettings = Seq(
  organization := "me.mig.mars",
  version := (version in ThisBuild).value,
  scalaVersion := "2.11.8",
  jira := sys.props.get("JIRA").getOrElse("QA-XXX")
)
// Use defaultMergeStrategy with a case for aop.xml
// I like this better than the inline version mentioned in assembly's README
lazy val customMergeStrategy: String => MergeStrategy = {
//  case PathList("META-INF", "aop.xml") => CustomMerge.aopMerge
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.discard
  case s => MergeStrategy.defaultMergeStrategy(s)
}

// Gatling testing
lazy val GTest = config("gatling") extend (Test)

lazy val mars = (project in file(".")).
  enablePlugins(PlayScala).
  enablePlugins(GatlingPlugin).
  configs(GTest).
  settings(inConfig(GTest)(Defaults.testSettings): _*).
  settings(
    scalaSource in GTest := baseDirectory.value / "/test",
    dependencyOverrides += "org.asynchttpclient" % "async-http-client" % "2.0.10" % Test
  ).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("play.core.server.ProdServerStart"),
    fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value),
    Defaults.coreDefaultSettings ++ sbtassembly.AssemblyPlugin.assemblySettings ++
      addArtifact(Artifact("mars", "assembly"), sbtassembly.AssemblyKeys.assembly) ++
      Seq(
        name := "mars",
        sbtassembly.AssemblyKeys.assemblyJarName <<= (name, scalaVersion, version in ThisBuild) map ((x,y,z) => "%s_%s-%s-assembly.jar" format(x,y,z))
      ),
    // Use the customMergeStrategy in your settings
    assemblyMergeStrategy in assembly := customMergeStrategy,

    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case "application.conf"                            => MergeStrategy.concat
      case "unwanted.txt"                                => MergeStrategy.discard
      case "javax/annotation/Syntax.class" => MergeStrategy.first
      case "javax/annotation/Syntax.java" => MergeStrategy.first
      case "javax/annotation/meta/When.class" => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },

    // Avoid sub-projects generating assembly jars
    aggregate in assembly := false,

    // Exclude tests in assembly
    test in assembly := {}
  )