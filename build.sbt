import Dependencies._

ThisBuild / scalaVersion     := "2.12.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "line-bot-lambda",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      // --[ Scala Library ]------------------------------------
      "org.scala-lang.modules" % "scala-java8-compat_2.12" % "0.9.0",

      // --[ Play Framework ]-----------------------------------
      "com.typesafe.play" %% "play-json" % "2.7.4",

      // --[ AWS SDK ]------------------------------------------
      "com.amazonaws" % "aws-lambda-java-core"   % "1.2.1",
      "com.amazonaws" % "aws-lambda-java-events" % "3.8.0",
      "com.amazonaws" % "aws-java-sdk-translate" % "1.11.800",

      // --[ LINE SDK ]-----------------------------------------
      "com.linecorp.bot" % "line-bot-model"      % "3.3.1",
      "com.linecorp.bot" % "line-bot-api-client" % "3.3.1",
    )
  )

// Setting for sbt assembly
assemblyJarName in assembly := { s"${ name.value }.jar" }
assemblyMergeStrategy in assembly := {
  case PathList(x @ _*) if x.last.endsWith(".class") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}


// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
