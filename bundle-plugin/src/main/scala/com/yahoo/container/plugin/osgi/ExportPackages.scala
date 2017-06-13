// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi

/**
 * @author  tonytv
 */
object ExportPackages {

  case class Export(packageNames: List[String], parameters: List[Parameter]) {
    def version: Option[String] = {
      (for (
        param <- parameters if param.name == "version"
      ) yield param.value).
        headOption
    }
  }

  case class Parameter(name: String, value: String)

  def exportsByPackageName(exports: Seq[Export]): Map[String, Export] = {
    (for {
      export <- exports.reverse  //ensure that earlier exports of a package overrides later exports.
      packageName <- export.packageNames
    } yield packageName -> export).
      toMap
  }
}
