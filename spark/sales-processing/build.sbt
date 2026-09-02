ThisBuild / scalaVersion := "2.12.20"

Compile / run / fork := true

Compile / run / javaOptions ++= Seq(
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)

lazy val root = (project in file("."))
  .settings(
    name := "sales-processing",
    version := "1.0.0",

    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % "3.5.6",
      "org.apache.spark" %% "spark-sql" % "3.5.6",
      "com.clickhouse.spark" % "clickhouse-spark-runtime-3.5_2.12" % "0.8.1"
    )
  )
