import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.ossPublishSettings

val scala2_12 = "2.12.18"
val scala2_13 = "2.13.11"
val scala3 = "3.3.0"
val scala2 = List(scala2_12, scala2_13)
val scala2And3 = List(scala2_12, scala2_13, scala3)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.akka-http-session",
  versionScheme := Some("early-semver")
)

val akkaHttpVersion = "10.2.7"
val akkaStreamsVersion = "2.6.18"
val json4sVersion = "4.0.4"
val akkaStreamsProvided = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion % "provided"
val akkaStreamsTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaStreamsVersion % "test"

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.16" % "test"

lazy val akkaRootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publish / skip := true, name := "akka-http-session", scalaVersion := scala2_13)
  .aggregate(core.projectRefs ++ jwt.projectRefs ++ example.projectRefs ++ javaTests.projectRefs: _*)

lazy val pekkoRootProject = (project in file("."))
  .settings(pekkoCommonSettings: _*)
  .settings(publish / skip := true, name := "pekko-http-session", scalaVersion := scala2_13)
  .aggregate(pekkoCore.projectRefs ++ pekkoJwt.projectRefs ++ pekkoExample.projectRefs ++ pekkoJavaTests.projectRefs: _*)

lazy val rootProject = (project in file("."))
  .settings(publish / skip := true, name := "akka-http-session-root", scalaVersion := scala2_13)
  .aggregate(akkaRootProject, pekkoRootProject)

lazy val core = (projectMatrix in file("core"))
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
  .jvmPlatform(scalaVersions = scala2)

lazy val jwt = (projectMatrix in file("jwt"))
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
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core)

lazy val example = (projectMatrix in file("example"))
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
      akkaStreamsProvided,
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

// Pekko build

lazy val pekkoCommonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.pekko-http-session",
  versionScheme := Some("early-semver")
)

val pekkoHttpVersion = "1.0.0"
val pekkoStreamsVersion = "1.0.1"

val pekkoStreamsProvided = "org.apache.pekko" %% "pekko-stream" % pekkoStreamsVersion % "provided"
val pekkoStreamsTestkit = "org.apache.pekko" %% "pekko-stream-testkit" % pekkoStreamsVersion % "test"
val scalaJava8CompatVersion = "1.0.2"

lazy val pekkoCore = (projectMatrix in file("pekko-http-session/core"))
  .settings(pekkoCommonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
      "org.scala-lang.modules" %% "scala-java8-compat" % scalaJava8CompatVersion,
      pekkoStreamsProvided,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % "test",
      pekkoStreamsTestkit,
      "org.scalacheck" %% "scalacheck" % "1.15.4" % "test",
      scalaTest
    )
  )
  .jvmPlatform(scalaVersions = scala2And3)

lazy val pekkoJwt = (projectMatrix in file("pekko-http-session/jwt"))
  .settings(pekkoCommonSettings: _*)
  .settings(
    name := "jwt",
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
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(pekkoCore)

lazy val pekkoExample = (projectMatrix in file("pekko-http-session/example"))
  .settings(pekkoCommonSettings: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      pekkoStreamsProvided,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.12",
      "org.json4s" %% "json4s-ext" % json4sVersion
    )
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(pekkoCore, pekkoJwt)

lazy val pekkoJavaTests = (projectMatrix in file("pekko-http-session/javaTests"))
  .settings(pekkoCommonSettings: _*)
  .settings(
    name := "javaTests",
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")), // required for javadsl JUnit tests
    crossPaths := false, // https://github.com/sbt/junit-interface/issues/35
    publishArtifact := false,
    libraryDependencies ++= Seq(
      pekkoStreamsProvided,
      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
      "org.scala-lang.modules" %% "scala-java8-compat" % scalaJava8CompatVersion,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % "test",
      pekkoStreamsTestkit,
      "junit" % "junit" % "4.13.2" % "test",
      "com.github.sbt" % "junit-interface" % "0.13.3" % "test",
      scalaTest
    )
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(pekkoCore, pekkoJwt)
