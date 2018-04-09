lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings  ++ Seq(
  organization := "com.softwaremill.akka-http-session",
  scalaVersion := "2.12.5",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12")
)

val akkaHttpVersion = "10.1.1"
val json4sVersion = "3.5.3"
val akkaStreamsProvided = "com.typesafe.akka" %% "akka-stream" % "2.5.11" % "provided"

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "akka-http-session")
  .aggregate(core, jwt, example, javaTests)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      akkaStreamsProvided,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
      scalaTest
    )
  )

lazy val jwt: Project = (project in file("jwt"))
  .settings(commonSettings: _*)
  .settings(
    name := "jwt",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      akkaStreamsProvided,
      scalaTest
    )
  ) dependsOn (core)

lazy val example: Project = (project in file("example"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      akkaStreamsProvided,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.json4s" %% "json4s-ext" % json4sVersion
    ))
  .dependsOn(core, jwt)

lazy val javaTests: Project = (project in file("javaTests"))
  .settings(commonSettings: _*)
  .settings(
    name := "javaTests",
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")), // required for javadsl JUnit tests
    crossPaths := false, // https://github.com/sbt/junit-interface/issues/35
    publishArtifact := false,
    libraryDependencies ++= Seq(
      akkaStreamsProvided,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      "junit" % "junit" % "4.12" % "test",
      "com.novocode" % "junit-interface" % "0.11" % "test",
      scalaTest
    ))
  .dependsOn(core, jwt)
