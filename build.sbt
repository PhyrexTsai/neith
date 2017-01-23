name := "neith-in-play"

version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  filters,
  "net.kaliber" %% "play-s3" % "8.0.0",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.8",
  specs2 % Test
)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

lazy val commonSettings = Seq(
)

lazy val neith = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)