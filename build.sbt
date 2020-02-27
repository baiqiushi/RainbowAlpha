import Dependencies._

name := """bigspatialviz"""
organization := "edu.uci.ics.cloudberry"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).
  settings(
    resolvers += "smile-core".at("https://mvnrepository.com/artifact/com.github.haifengl/smile-core")
  ).
  settings(
    libraryDependencies ++= clustervizDependencies
  ).
  settings(
    mappings in Universal ++=
      (baseDirectory.value / "public" / "data" * "*" get) map
        (x => x -> ("public/data/" + x.getName))
  ).enablePlugins(PlayJava)

scalaVersion := "2.13.0"

libraryDependencies += guice

PlayKeys.devSettings += "play.server.http.idleTimeout" -> "3600 seconds"
