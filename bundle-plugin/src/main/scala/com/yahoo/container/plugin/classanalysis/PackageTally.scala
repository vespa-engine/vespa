// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import com.yahoo.container.plugin.util.Maps

/**
 *
 * @author  tonytv
 */
final class PackageTally (private val definedPackagesMap : Map[String, Option[ExportPackageAnnotation]],
                          referencedPackagesUnfiltered : Set[String]) {

  val referencedPackages = referencedPackagesUnfiltered diff definedPackages

  def definedPackages = definedPackagesMap.keySet

  def exportedPackages = definedPackagesMap collect { case (name, Some(export)) => (name, export) }

  /**
   * Represents the classes for two package tallies that are deployed as a single unit.
   *
   * ExportPackageAnnotations from this has precedence over the other.
   */
  def combine(other: PackageTally): PackageTally = {
    new PackageTally(
      Maps.combine(definedPackagesMap, other.definedPackagesMap)(_ orElse _),
      referencedPackages ++ other.referencedPackages)
  }
}


object PackageTally {
  def fromAnalyzedClassFiles(analyzedClassFiles : Seq[ClassFileMetaData]) : PackageTally = {
    combine(
      for (metaData <- analyzedClassFiles)
      yield {
        new PackageTally(
          Map(Packages.packageName(metaData.name) -> metaData.exportPackage),
          metaData.referencedClasses.map(Packages.packageName))
      })
  }

  def combine(packageTallies : Iterable[PackageTally]) : PackageTally = (empty /: packageTallies)(_.combine(_))

  val empty : PackageTally = new PackageTally(Map(), Set())
}
