import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.ossPublishSettings

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.akka-http-session",
  scalaVersion := "2.13.8",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.15"),
  versionScheme := Some("early-semver")
)

val akkaHttpVersion = "10.2.7"
val akkaStreamsVersion = "2.6.18"
val json4sVersion = "4.0.4"
val akkaStreamsProvided = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion % "provided"
val akkaStreamsTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaStreamsVersion % "test"

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.11" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "akka-http-session")
  .aggregate(core, jwt, example, javaTests)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      akkaStreamsProvided,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      akkaStreamsTestkit,
      "org.scalacheck" %% "scalacheck" % "1.15.4" % "test",
      scalaTest
    )
  )

lazy val jwt: Project = (project in file("jwt"))
  .settings(commonSettings: _*)
  .settings(
    name := "jwt",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ast" % json4sVersion,
      "org.json4s" %% "json4s-core" % json4sVersion,
      akkaStreamsProvided,
      scalaTest
    ),
    // generating docs for 2.13 causes an error: "not found: type DefaultFormats$"
    Compile / doc / sources := Seq.empty
  ) dependsOn (core)

lazy val example: Project = (project in file("example"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      akkaStreamsProvided,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.10",
      "org.json4s" %% "json4s-ext" % json4sVersion
    )
  )
  .dependsOn(core, jwt)

lazy val javaTests: Project = (project in file("javaTests"))
  .settings(commonSettings: _*)
  .settings(
    name := "javaTests",
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")), // required for javadsl JUnit tests
    crossPaths := false, // https://github.com/sbt/junit-interface/issues/35
    publishArtifact := false,
    libraryDependencies ++= Seq(
      akkaStreamsProvided,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      akkaStreamsTestkit,
      "junit" % "junit" % "4.13.2" % "test",
      "com.github.sbt" % "junit-interface" % "0.13.3" % "test",
      scalaTest
    )
  )
  .dependsOn(core, jwt)
