import scalariform.formatter.preferences._

lazy val commonSettings = scalariformSettings ++ Seq(
  organization := "com.softwaremill",
  version := "0.1.3",
  scalaVersion := "2.11.6",
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
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),publishMavenStyle := true,
  pomIncludeRepository := { _ => false },pomExtra := (
    <scm>
      <url>git@gihub.com/softwaremill/akka-http-session.git</url>
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

val akkaHttpVersion = "1.0"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "akka-http-session-root")
  .aggregate(impl, example)

lazy val impl: Project = (project in file("impl"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-http-session",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaHttpVersion % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.4" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    )
  )

lazy val example: Project = (project in file("example"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
      "ch.qos.logback" % "logback-classic" % "1.1.3"
    ))
  .dependsOn(impl)
