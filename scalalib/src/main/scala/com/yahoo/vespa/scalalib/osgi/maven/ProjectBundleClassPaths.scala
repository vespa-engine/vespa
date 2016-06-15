// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.scalalib.osgi.maven

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.json4s.NoTypeHints
import org.json4s.native.Serialization

import ProjectBundleClassPaths._

/**
 * Represents the bundles in a maven project and the classpath elements
 * corresponding to code that would end up in the bundle.
 * @author tonytv
 */
case class ProjectBundleClassPaths(mainBundle: BundleClasspathMapping,
                                   providedDependencies: List[BundleClasspathMapping])


object ProjectBundleClassPaths {
  //Written by bundle-plugin in the test classes directory.
  val classPathMappingsFileName = "bundle-plugin.bundle-classpath-mappings.json"

  case class BundleClasspathMapping(bundleSymbolicName: String, classPathElements: List[String])

  //Used by Serialization.* methods. See https://github.com/json4s/json4s
  private implicit val serializationFormat = Serialization.formats(NoTypeHints)

  def save(path: Path, mappings: ProjectBundleClassPaths) {
    Files.createDirectories(path.getParent)
    val outputFile = Files.newBufferedWriter(path, StandardCharsets.UTF_8)
    try {
      Serialization.write(mappings, outputFile)
    } finally {
      outputFile.close()
    }
  }

  def load(path: Path): ProjectBundleClassPaths = {
    val inputFile = Files.newBufferedReader(path, StandardCharsets.UTF_8)
    Serialization.read[ProjectBundleClassPaths](inputFile)
  }
}
