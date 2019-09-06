name := "nwbib"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  cache,
  javaWs,
  "com.typesafe.play" % "play-test_2.11" % "2.4.11",
  "org.elasticsearch" % "elasticsearch" % "1.7.5" withSources(),
  "org.mockito" % "mockito-core" % "1.9.5",
  "com.github.jsonld-java" % "jsonld-java" % "0.5.0",
  "org.apache.commons" % "commons-rdf-jena" % "0.5.0",
  "org.apache.commons" % "commons-csv" % "1.6",
  "org.apache.jena" % "jena-arq" % "3.0.1",
  "org.apache.jena" % "jena-core" % "3.0.1",
  "org.easytesting" % "fest-assert" % "1.4" % "test"
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

 EclipseKeys.projectFlavor := EclipseProjectFlavor.Java // Java project. Don't expect Scala IDE
 EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources) // Use .class files instead of generated .scala files for views and routes
 EclipseKeys.preTasks := Seq(compile in Compile) // Compile the project before generating Eclipse files, so that .class files for views and routes are present

trapExit := false
