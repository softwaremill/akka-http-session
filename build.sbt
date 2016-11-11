import scalariform.formatter.preferences._

lazy val commonSettings = scalariformSettings ++ Seq(
  organization := "com.softwaremill.akka-http-session",
  version := "0.3.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.0"),
  scalacOptions ++= Seq("-unchecked", "-deprecation"),
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(CompactControlReadability, true)
    .setPreference(SpacesAroundMultiImports, false),
  // Sonatype OSS deployment
  publishTo <<= version { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  pomExtra := (
    <scm>
      <url>git@github.com/softwaremill/akka-http-session.git</url>
      <connection>scm:git:git@github.com/softwaremill/akka-http-session.git</connection>
    </scm>
      <developers>
        <developer>
          <id>adamw</id>
          <name>Adam Warski</name>
          <url>http://www.warski.org</url>
        </developer>
      </developers>
    ),
  licenses := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil,
  homepage := Some(new java.net.URL("http://softwaremill.com"))
)

val akkaHttpVersion = "10.0.0-RC2"
val akkaVersion = "2.4.12"

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "akka-http-session")
  .aggregate(core, jwt, example)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
      scalaTest
    )
  )

lazy val jwt: Project = (project in file("jwt"))
  .settings(commonSettings: _*)
  .settings(
    name := "jwt",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % "3.5.0",
      scalaTest
    )
  ) dependsOn(core)

lazy val example: Project = (project in file("example"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "ch.qos.logback" % "logback-classic" % "1.1.7"
    ))
  .dependsOn(core, jwt)
