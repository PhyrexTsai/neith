import sbt.Attributed
import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

name := """mars"""

scalaVersion := "2.11.8"

val akkaVersion = "2.4.9"

libraryDependencies ++= Seq(
//  jdbc,
  cache,
  ws,
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "im.actor" % "akka-scalapb-serialization_2.11" % "0.1.14",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.11-RC1",
  "com.typesafe.play" %% "play-mailer" % "5.0.0",
  "com.typesafe.play" % "twirl-compiler_2.11" % "1.2.0",
  // Datastores
  "mysql" % "mysql-connector-java" % "5.1.34",
  "com.typesafe.play" %% "play-slick" % "2.0.2",
  // Logging
  "net.logstash.logback" % "logstash-logback-encoder" % "4.7",
  // Nexus dependencies
  "me.mig" %% "matter-stream" % "1.0.2"
)

// Exclude commons-logging since Play has jcl-over-slf4j, which re-implements the logging API.
libraryDependencies ~= { _ map {
  case m if m.organization == "com.typesafe.play" =>
    m.exclude("commons-logging", "commons-logging").
      exclude("com.typesafe.play", "sbt-link")
  case m => m
}}

// ==============================================
//  RPM Settigns
// ==============================================
// Settings for sbt-native-packager
enablePlugins(RpmPlugin)

maintainer in Rpm := "devteam <devteam@mig.me>"

packageSummary in Rpm := "Notification service"

packageDescription in Rpm := "Provide notifications include Email, SMS, APNS and GCM."

rpmRelease := "1"

rpmVendor := "mig.me"

rpmUrl := Some("http://github.com/example/server")

rpmLicense := Some("Migme Ltd.")

//=======================================================================================
// Publish
//=======================================================================================
// Publish configurations
publishMavenStyle := true

publishArtifact in Test := false

publishTo := {
  val nexus = "https://tools.projectgoth.com/nexus/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "content/repositories/releases")
}

// ==============================================
//  Assembly Settigns
// ==============================================
lazy val jira = SettingKey[String]("jira", "The JIRA issue parameter to be propagated to git commit message.")

lazy val commonSettings = Seq(
  organization := "me.mig.mars",
  version := (version in ThisBuild).value,
  jira := sys.props.get("JIRA").getOrElse("QA-XXX")
)
// Use defaultMergeStrategy with a case for aop.xml
// I like this better than the inline version mentioned in assembly's README
lazy val customMergeStrategy: String => MergeStrategy = {
//  case PathList("META-INF", "aop.xml") => CustomMerge.aopMerge
  case PathList("META-INF", "io.netty.versions.properties") => CustomMerge.nettyMerge
  case s => MergeStrategy.defaultMergeStrategy(s)
}

lazy val mars = (project in file(".")).
  enablePlugins(PlayScala).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("play.core.server.ProdServerStart"),
    fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value),
//    Defaults.coreDefaultSettings ++ sbtassembly.AssemblyPlugin.assemblySettings ++
//      addArtifact(Artifact("mars", "assembly"), sbtassembly.AssemblyKeys.assembly) ++
//      Seq(
//        name := "mars",
//        sbtassembly.AssemblyKeys.assemblyJarName <<= (name, scalaVersion in ThisBuild, version in ThisBuild) map ((x,y,z) => "%s_%s-%s-assembly.jar" format(x,y,z))
//      ),
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