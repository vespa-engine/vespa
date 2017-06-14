// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

/**
 * Utility methods related to packages.
 * @author  tonytv
 */
object Packages {
  case class PackageMetaData(definedPackages: Set[String], referencedExternalPackages: Set[String])

  def packageName(fullClassName: String) = {
    def nullIfNotFound(index : Int) = if (index == -1) 0 else index

    fullClassName.substring(0, nullIfNotFound(fullClassName.lastIndexOf(".")))
  }



  def analyzePackages(allClasses: Seq[ClassFileMetaData]): PackageMetaData = {
    val (definedPackages, referencedClasses) =
      (for (classMetaData <- allClasses)
      yield (packageName(classMetaData.name), classMetaData.referencedClasses.map(packageName))).
        unzip

    PackageMetaData(definedPackages.toSet, referencedClasses.flatten.toSet diff definedPackages.toSet)
  }
}
