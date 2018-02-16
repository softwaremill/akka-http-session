import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

lazy val commonSettings = Seq(
  organization       := "com.softwaremill.akka-http-session",
  version            := "0.5.x",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
  homepage           := Some(url("http://softwaremill.com")),
  licenses           += ("Apache2", url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalacOptions     ++= Seq("-unchecked", "-deprecation"),
  scalaVersion       := "2.12.4",
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(CompactControlReadability, true)
    .setPreference(SpacesAroundMultiImports, false),
  // Sonatype OSS deployment
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  pomExtra :=
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
)

val akkaHttpVersion = "10.1.0-RC1"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
cancelable in Global := true // Gracefully stops your running app by ctrl+d or ctrl+z

lazy val rootProject: Project = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "akka-http-session")
  .aggregate(core, jwt, example, javaTests)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings,
    name := "core",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"      % "2.5.9",
      "com.typesafe.akka" %% "akka-http-testkit"% akkaHttpVersion % Test,
      "org.scalacheck"    %% "scalacheck"       % "1.13.5"        % Test,
      scalaTest
    )
  )

lazy val jwt: Project = (project in file("jwt"))
  .settings(commonSettings,
    name := "jwt",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % "3.5.3",
      scalaTest
    )
  ) dependsOn core

lazy val example: Project = (project in file("example"))
  .settings(commonSettings,
    javaOptions ++= Seq("--add-modules=java.xml.bind"),
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "ch.qos.logback"             % "logback-classic"% "1.2.3",
      "com.typesafe.scala-logging" %%"scala-logging"  % "3.7.2",
      "org.json4s"                 %% "json4s-ext"    % "3.5.0"
    ))
  .dependsOn(core, jwt)

lazy val javaTests: Project = (project in file("javaTests"))
  .settings(commonSettings,
    name := "javaTests",
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")), // required for javadsl JUnit tests
    crossPaths := false, // https://github.com/sbt/junit-interface/issues/35
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion,
      "com.novocode" % "junit-interface"         % "0.11"          % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "junit"              % "junit"             % "4.12"          % Test,
      scalaTest
    ))
  .dependsOn(core, jwt)