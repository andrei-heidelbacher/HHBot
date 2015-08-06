name := "HHBot"

version := "1.0"

scalaVersion := "2.11.7"

crossPaths := false

resolvers +=
  "Typesafe Repository" at "repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.4",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.4",
  "com.typesafe.akka" %% "akka-remote" % "2.3.4",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.4",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.4" % "test"
)

libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"