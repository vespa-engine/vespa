// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.bundle

import org.scalatest.junit.{JUnitSuite, ShouldMatchersForJUnit}
import org.junit.Test
import com.yahoo.container.plugin.bundle.AnalyzeBundle.PublicPackages
import com.yahoo.container.plugin.osgi.ExportPackages
import java.io.File

/**
 * @author  tonytv
 */
class AnalyzeBundleTest extends JUnitSuite with ShouldMatchersForJUnit {
  val jarDir = new File("src/test/resources/jar")

  val PublicPackages(exports, globals) = AnalyzeBundle.publicPackagesAggregated(
    List("notAOsgiBundle.jar", "simple1.jar").map(jarFile))

  val exportsByPackageName = ExportPackages.exportsByPackageName(exports)

  def jarFile(name : String) : File = new File(jarDir, name)

  @Test
  def require_that_non_osgi_bundles_are_ignored() {
    exportsByPackageName should not contain key("com.yahoo.sample.exported.package.ignored")
  }

  @Test
  def require_that_exports_are_retrieved_from_manifest_in_jars() {
    exportsByPackageName.keySet should be (Set("com.yahoo.sample.exported.package"))
  }

  @Test
  def require_that_invalid_exports_throws_exception() {
    val exception = intercept[Exception](AnalyzeBundle.publicPackages(jarFile("errorExport.jar")))
    exception.getMessage should startWith regex ("Invalid manifest in bundle '.*errorExport.jar'")
    exception.getCause.getMessage should startWith ("Failed parsing Export-Package")
  }
}
