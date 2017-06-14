// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.bundle

import java.util.jar.{Manifest => JarManifest}
import java.io.File
import com.yahoo.container.plugin.osgi.{ExportPackages, ExportPackageParser}
import ExportPackages.Export
import collection.immutable.LinearSeq
import com.yahoo.container.plugin.util.JarFiles


/**
 * @author  tonytv
 */
object AnalyzeBundle {
  case class PublicPackages(exports : List[Export], globals : List[String])

  def publicPackagesAggregated(jarFiles : Iterable[File]) = aggregate(jarFiles map  {publicPackages(_)})

  def aggregate(publicPackagesList : Iterable[PublicPackages]) =
    (PublicPackages(List(), List()) /: publicPackagesList) { (a,b) =>
      PublicPackages(a.exports ++ b.exports, a.globals ++ b.globals)
    }

  def publicPackages(jarFile: File): PublicPackages = {
    try {

      (for {
        manifest <- JarFiles.getManifest(jarFile)
        if isOsgiManifest(manifest)
      } yield PublicPackages(parseExports(manifest), parseGlobals(manifest))).
      getOrElse(PublicPackages(List(), List()))

    } catch {
      case e : Exception => throw new RuntimeException("Invalid manifest in bundle '%s'".format(jarFile.getPath), e)
    }
  }

  def bundleSymbolicName(jarFile: File): Option[String] = {
    JarFiles.getManifest(jarFile).flatMap(getBundleSymbolicName)
  }

  private def parseExportsFromAttribute(manifest : JarManifest, attributeName : String) = {
    (for (export <- getMainAttributeValue(manifest, attributeName)) yield
      ExportPackageParser.parseAll(export) match {
        case noSuccess: ExportPackageParser.NoSuccess => throw new RuntimeException(
          "Failed parsing %s: %s".format(attributeName, noSuccess))
        case success => success.get
      }).
      getOrElse(List())
  }

  private def parseExports = parseExportsFromAttribute(_ : JarManifest, "Export-Package")

  private def parseGlobals(manifest : JarManifest) = {
    //TODO: Use separate parser for global packages.
    val globals = parseExportsFromAttribute(manifest, "Global-Package")

    if (globals map {_.parameters} exists {!_.isEmpty}) {
      throw new RuntimeException("Parameters not valid for Global-Package.")
    }

    globals flatMap {_.packageNames}
  }

  private def getMainAttributeValue(manifest: JarManifest, name: String): Option[String] =
    Option(manifest.getMainAttributes.getValue(name))

  private def isOsgiManifest = getBundleSymbolicName(_: JarManifest).isDefined

  private def getBundleSymbolicName = getMainAttributeValue(_: JarManifest, "Bundle-SymbolicName")
}
