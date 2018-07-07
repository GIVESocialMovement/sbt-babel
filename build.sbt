lazy val `sbt-babel` = project in file(".")

organization := "givers.babel"
name := "sbt-babel"

scalaVersion := "2.12.5"

publishMavenStyle := true

bintrayOrganization := Some("givers")

bintrayRepository := "maven"

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/GIVESocialMovement/sbt-babel"),
  connection = "scm:git:git@github.com:GIVESocialMovement/sbt-babel.git",
  devConnection = Some("scm:git:git@github.com:GIVESocialMovement/sbt-babel.git")
))

addSbtJsEngine("1.2.2")
