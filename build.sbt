import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.ossPublishSettings

val scala2_12 = "2.12.17"
val scala2_13 = "2.13.10"
val scala2 = List(scala2_12, scala2_13)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.github.pjfanning",
  versionScheme := Some("early-semver"),
  resolvers += "Apache Pekko Staging" at "https://repository.apache.org/content/groups/staging"
)

val pekkoHttpVersion = "1.0.0-RC2"
val pekkoStreamsVersion = "1.0.1"
val json4sVersion = "4.0.4"
val pekkoStreamsProvided = "org.apache.pekko" %% "pekko-stream" % pekkoStreamsVersion % "provided"
val pekkoStreamsTestkit = "org.apache.pekko" %% "pekko-stream-testkit" % pekkoStreamsVersion % "test"

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.16" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publish / skip := true, name := "pekko-http-session-root", scalaVersion := scala2_13)
  .aggregate(core.projectRefs ++ jwt.projectRefs ++ example.projectRefs ++ javaTests.projectRefs: _*)

lazy val core = (projectMatrix in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "pekko-http-session-core",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
      pekkoStreamsProvided,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % "test",
      pekkoStreamsTestkit,
      "org.scalacheck" %% "scalacheck" % "1.15.4" % "test",
      scalaTest
    )
  )
  .jvmPlatform(scalaVersions = scala2)

lazy val jwt = (projectMatrix in file("jwt"))
  .settings(commonSettings: _*)
  .settings(
    name := "pekko-http-session-jwt",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ast" % json4sVersion,
      "org.json4s" %% "json4s-core" % json4sVersion,
      pekkoStreamsProvided,
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
      pekkoStreamsProvided,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.12",
      "org.json4s" %% "json4s-ext" % json4sVersion
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core, jwt)

lazy val javaTests = (projectMatrix in file("javaTests"))
  .settings(commonSettings: _*)
  .settings(
    name := "javaTests",
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")), // required for javadsl JUnit tests
    crossPaths := false, // https://github.com/sbt/junit-interface/issues/35
    publishArtifact := false,
    libraryDependencies ++= Seq(
      pekkoStreamsProvided,
      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % "test",
      pekkoStreamsTestkit,
      "junit" % "junit" % "4.13.2" % "test",
      "com.github.sbt" % "junit-interface" % "0.13.3" % "test",
      scalaTest
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core, jwt)
