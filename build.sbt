import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.ossPublishSettings

val scala2_12 = "2.12.15"
val scala2_13 = "2.13.8"
val scala2 = List(scala2_12, scala2_13)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.akka-http-session",
  versionScheme := Some("early-semver")
)

val akkaHttpVersion = "10.2.7"
val akkaVersion = "2.6.18"
val json4sVersion = "4.0.4"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val akkaStreamsTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test"
val tapirVersion = "1.3.0"

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.11" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publish / skip := true, name := "akka-http-session", scalaVersion := scala2_13)
  .aggregate(core.projectRefs ++ jwt.projectRefs ++ example.projectRefs ++ javaTests.projectRefs ++ tapir.projectRefs: _*)

lazy val core = (projectMatrix in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      akkaStreams % "provided",
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      akkaStreamsTestkit,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "org.scalacheck" %% "scalacheck" % "1.15.4" % "test",
      scalaTest
    )
  )
  .jvmPlatform(scalaVersions = scala2)

lazy val jwt = (projectMatrix in file("jwt"))
  .settings(commonSettings: _*)
  .settings(
    name := "jwt",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ast" % json4sVersion,
      "org.json4s" %% "json4s-core" % json4sVersion,
      akkaStreams % "provided",
      scalaTest
    ),
    // generating docs for 2.13 causes an error: "not found: type DefaultFormats$"
    Compile / doc / sources := Seq.empty
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core)

lazy val example = (projectMatrix in file("example"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      akkaStreams,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.10",
      "org.json4s" %% "json4s-ext" % json4sVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(tapir, jwt)

lazy val javaTests = (projectMatrix in file("javaTests"))
  .settings(commonSettings: _*)
  .settings(
    name := "javaTests",
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")), // required for javadsl JUnit tests
    crossPaths := false, // https://github.com/sbt/junit-interface/issues/35
    publishArtifact := false,
    libraryDependencies ++= Seq(
      akkaStreams % "provided",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      akkaStreamsTestkit,
      "junit" % "junit" % "4.13.2" % "test",
      "com.github.sbt" % "junit-interface" % "0.13.3" % "test",
      scalaTest
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core, jwt)

lazy val tapir = (projectMatrix in file("tapir"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % tapirVersion excludeAll ExclusionRule(organization = "com.typesafe.akka")
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core % "compile->compile;test->test")
