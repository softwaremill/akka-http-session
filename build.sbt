import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.ossPublishSettings

val scala2_12 = "2.12.17"
val scala2_13 = "2.13.10"
val scala2 = List(scala2_12, scala2_13)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.pekko-http-session",
  versionScheme := Some("early-semver"),
  resolvers += "Apache Snapshots" at "https://repository.apache.org/content/groups/snapshots"
)

val akkaHttpVersion = "0.0.0+4269-0eb7860f-SNAPSHOT"
val akkaStreamsVersion = "0.0.0+26544-4c021960-SNAPSHOT"

val json4sVersion = "4.0.6"
val akkaStreamsProvided = "org.apache.pekko" %% "pekko-stream" % akkaStreamsVersion % "provided"
val akkaStreamsTestkit = "org.apache.pekko" %% "pekko-stream-testkit" % akkaStreamsVersion % "test"

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publish / skip := true, name := "pekko-http-session", scalaVersion := scala2_13)
  .aggregate(core.projectRefs ++ jwt.projectRefs ++ example.projectRefs ++ javaTests.projectRefs: _*)

lazy val core = (projectMatrix in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http" % akkaHttpVersion,
      akkaStreamsProvided,
      "org.apache.pekko" %% "pekko-http-testkit" % akkaHttpVersion % "test",
      akkaStreamsTestkit,
      "org.scalacheck" %% "scalacheck" % "1.17.0" % "test",
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
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "ch.qos.logback" % "logback-classic" % "1.4.5",
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
      "org.apache.pekko" %% "pekko-http" % akkaHttpVersion,
      "org.apache.pekko" %% "pekko-http-testkit" % akkaHttpVersion % "test",
      akkaStreamsTestkit,
      "junit" % "junit" % "4.13.2" % "test",
      "com.github.sbt" % "junit-interface" % "0.13.3" % "test",
      scalaTest
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core, jwt)
