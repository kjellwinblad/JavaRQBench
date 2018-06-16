name := "scala_datatypes"

version := "1.0"

scalaVersion := "2.11.1"

//javacOptions += "-g"

resolvers ++= Seq(
  "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
)

//libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"


libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.1.7" % "test"

libraryDependencies += "org.scala-lang" % "scala-actors" % "2.11.1" % "test"

libraryDependencies += "org.scala-lang" % "scala-actors" % "2.11.1" 

javacOptions in ThisBuild ++= Seq("-Xlint:unchecked", "-g")
