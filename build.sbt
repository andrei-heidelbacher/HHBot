name := "HHBot"

version := "0.1.0"

scalaVersion := "2.11.7"

crossPaths := false

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xlint",
  "-Xfuture",
  "-Xfatal-warnings",
  "-Ywarn-dead-code"
)

resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  DefaultMavenRepository
)

lazy val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.4",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.4",
  "com.typesafe.akka" %% "akka-remote" % "2.3.4",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.4",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.4" % "test"
)
lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"
lazy val scalarobots = "scala-robots" % "scala-robots" % "1.1.4" from
  "https://github.com/andrei-heidelbacher/scala-robots/releases/download/" +
  "v1.1.4/scala-robots.jar"
lazy val scalatest = "org.scalatest" %% "scalatest" % "2.1.3" % "test"

libraryDependencies ++= akka ++ Seq(dispatch, logback, scalarobots, scalatest)

mainClass := Some("hhbot.runner.DefaultRunner")