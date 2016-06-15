// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi

import org.scalatest.junit.{JUnitSuite, ShouldMatchersForJUnit}
import org.junit.Test

import ImportPackages.Import
import ExportPackages.{Parameter, Export}

/**
 * @author  tonytv
 */
class ImportPackageTest extends JUnitSuite with ShouldMatchersForJUnit {
  val referencedPackages = Set("com.yahoo.exported")
  val exports = exportByPackageName(Export(List("com.yahoo.exported"), List()))
  val exportsWithVersion = exportByPackageName(exports.head._2.copy(parameters = List(Parameter("version", "1.3"))))

  def exportByPackageName(export : Export) = ExportPackages.exportsByPackageName(List(export))
  @Test
  def require_that_non_implemented_import_with_matching_export_is_included() {
    val imports = calculateImports(referencedPackages, implementedPackages = Set(), exportedPackages = exports)
    imports should be (Set(Import("com.yahoo.exported", None)))
  }


  @Test
  def require_that_non_implemented_import_without_matching_export_is_excluded() {
    val imports = calculateImports(referencedPackages, implementedPackages = Set(), exportedPackages = Map())
    imports should be (Set())
  }

  @Test
  def require_that_implemented_import_with_matching_export_is_excluded() {
    val imports = calculateImports(
      referencedPackages,
      implementedPackages = referencedPackages,
      exportedPackages = exports)

    imports should be (Set())
  }

  @Test
  def require_that_version_is_included() {
    val imports = calculateImports(referencedPackages, implementedPackages = Set(), exportedPackages = exportsWithVersion)

    imports should be (Set(Import("com.yahoo.exported", Some("1.3"))))
  }

  @Test
  def require_that_all_versions_up_to_the_next_major_version_is_in_range() {
    Import("foo", Some("1.2")).importVersionRange should be (Some("[1.2,2)"))
  }

  // TODO: Detecting guava packages should be based on bundle-symbolicName, not package name.
  @Test
  def require_that_for_guava_all_future_major_versions_are_in_range() {
    val rangeWithInfiniteUpperLimit = Some("[18.1," + ImportPackages.InfiniteVersion + ")")
    Import("com.google.common",     Some("18.1")).importVersionRange should be (rangeWithInfiniteUpperLimit)
    Import("com.google.common.foo", Some("18.1")).importVersionRange should be (rangeWithInfiniteUpperLimit)

    Import("com.google.commonality", Some("18.1")).importVersionRange should be (Some("[18.1,19)"))
  }

  @Test
  def require_that_none_version_gives_non_version_range() {
    Import("foo", None).importVersionRange should be (None)
  }

  @Test
  def require_that_exception_is_thrown_when_major_component_is_non_numeric() {
    intercept[IllegalArgumentException](Import("foo", Some("1notValid.2")))
  }

  @Test
  def require_that_osgi_import_supports_missing_version() {
    Import("com.yahoo.exported", None).asOsgiImport should be ("com.yahoo.exported")
  }

  @Test
  def require_that_osgi_import_version_range_includes_all_versions_from_the_current_up_to_the_next_major_version() {
    Import("com.yahoo.exported", Some("1.2")).asOsgiImport should be ("com.yahoo.exported;version=\"[1.2,2)\"")
  }

  @Test
  def require_that_osgi_import_version_range_ignores_qualifier() {
    Import("com.yahoo.exported", Some("1.2.3.qualifier")).asOsgiImport should be ("com.yahoo.exported;version=\"[1.2.3,2)\"")
  }


  def calculateImports(referencedPackages : Set[String],
                       implementedPackages : Set[String],
                       exportedPackages : Map[String, Export]) : Set[Import] = {
    ImportPackages.calculateImports(referencedPackages, implementedPackages, exportedPackages).values.toSet
  }
}
