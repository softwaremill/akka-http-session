val sbtSoftwareMillVersion = "2.0.9"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-browser-test-js" % sbtSoftwareMillVersion)

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.21")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")
