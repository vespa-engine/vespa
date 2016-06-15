// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi

import ExportPackages.Export
import util.control.Exception

/**
 * @author  tonytv
 */
object ImportPackages {
  case class Import(packageName : String, version : Option[String]) {
    val majorMinorMicroVersion = Exception.handling(classOf[NumberFormatException]).
      by( e => throw new IllegalArgumentException(
      "Invalid version number '%s' for package '%s'.".format(version.get, packageName), e)) {

      version map { _.split('.') take 3 map {_.toInt} }
    }

    def majorVersion = majorMinorMicroVersion map { _.head }

    // TODO: Detecting guava packages should be based on Bundle-SymbolicName, not package name.
    def importVersionRange = {
      def upperLimit =
        if (isGuavaPackage) InfiniteVersion   // guava increases major version for each release
        else majorVersion.get + 1

      version map (v => "[%s,%s)".format(majorMinorMicroVersion.get.mkString("."), upperLimit))
    }

    def isGuavaPackage = packageName.equals(GuavaBasePackage) || packageName.startsWith(GuavaBasePackage + ".")

    def asOsgiImport = packageName + (importVersionRange map {";version=\"" + _ + '"'} getOrElse(""))
  }


  val GuavaBasePackage = "com.google.common"
  val InfiniteVersion = 99999

  def calculateImports(referencedPackages : Set[String],
                       implementedPackages : Set[String],
                       exportedPackages : Map[String, Export]) : Map[String, Import] = {
    (for {
      undefinedPackage <- referencedPackages diff implementedPackages
      export <- exportedPackages.get(undefinedPackage)
    } yield undefinedPackage -> Import(undefinedPackage, version(export)))(
      collection.breakOut)
  }

  def version(export: Export): Option[String] =
    export.parameters.find(_.name == "version").map(_.value)
}
