// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
name := "blog-support"

version := "0.1"

scalaVersion := "2.11.8"
//scalaVersion := "2.10.5"

// unit test
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.6" % "test"
//libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.0" % "test"

// spark libraries
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.0.0",
  "org.apache.spark" %% "spark-mllib" % "2.0.0",
  "org.apache.spark" %% "spark-sql" % "2.0.0"
)

//libraryDependencies ++= Seq(
//  "org.apache.spark" %% "spark-core" % "1.6.2",
//  "org.apache.spark" %% "spark-mllib" % "1.6.2",
//  "org.apache.spark" %% "spark-sql" % "1.6.2"
//)


