name := """airlines-akka-rest"""
organization := "com.ncsu.atit"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.2"

libraryDependencies += guice

libraryDependencies += javaJdbc

//libraryDependencies += javaJpa

//libraryDependencies += evolutions

//libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.8.7"

//libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7"

// https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.20.0"

fork in run := false

EclipseKeys.preTasks := Seq(compile in Compile, compile in Test)

EclipseKeys.projectFlavor := EclipseProjectFlavor.Java           // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead of generated .scala files for views and routes
