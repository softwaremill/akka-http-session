import scalariform.formatter.preferences._

lazy val commonSettings = scalariformSettings ++ Seq(
  organization := "com.softwaremill.akka-http-session",
  version := "0.2.7",
  scalaVersion := "2.11.8",
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

val akkaVersion = "2.4.9"

val scalaTest = "org.scalatest" %% "scalatest" % "2.2.6" % "test"

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
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.5" % "test",
      scalaTest
    )
  )

lazy val jwt: Project = (project in file("jwt"))
  .settings(commonSettings: _*)
  .settings(
    name := "jwt",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      scalaTest
    )
  ) dependsOn(core)

lazy val example: Project = (project in file("example"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
      "ch.qos.logback" % "logback-classic" % "1.1.7"
    ))
  .dependsOn(core, jwt)
