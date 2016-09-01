name := """mars"""

version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, RpmPlugin)

scalaVersion := "2.11.8"

val akkaVersion = "2.4.9"

libraryDependencies ++= Seq(
  jdbc,
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
  // Nexus dependencies
  "me.mig" %% "matter-stream" % "1.0.2"
)

// Settings for sbt-native-packager
maintainer in Rpm := "devteam <devteam@mig.me>"

packageSummary in Rpm := "Notification service"

packageDescription in Rpm := "Provide notifications include Email, SMS, APNS and GCM."

rpmRelease := "1"

rpmVendor := "mig.me"

rpmUrl := Some("http://github.com/example/server")

rpmLicense := Some("Migme Ltd.")