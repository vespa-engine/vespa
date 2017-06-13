// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.bundle

import java.io.File
import com.yahoo.container.plugin.osgi.ExportPackages.{Export, Parameter}
import com.yahoo.container.plugin.osgi.ExportPackages.Export

/**
 * @author tonytv
 */
object TransformExportPackages extends App {
  def replaceVersions(exports: List[Export], newVersion: String): List[Export] = {
    mapParameters(exports) { parameters =>
      parameters map replaceVersion(newVersion)
    }
  }

  def removeUses(exports: List[Export]): List[Export] = {
    mapParameters(exports) { parameters =>
      parameters filter {_.name != "uses"}
    }
  }

  def mapParameters(exports: List[Export])(f: List[Parameter] => List[Parameter]): List[Export] = {
    exports map { case Export(packageNames: List[String], parameters: List[Parameter]) =>
        Export(packageNames, f(parameters))
    }
  }

  private def replaceVersion(newVersion: String)(parameter: Parameter) = {
    parameter match {
      case Parameter("version", _) => Parameter("version", newVersion)
      case other => other
    }
  }

  def toExportPackageProperty(exports: List[Export]): String = {
    val exportPackages =
      exports map { case Export(packageNames: List[String], parameters: List[Parameter]) =>
        val parameterString = nameEqualsValue(parameters)
        (packageNames ++ parameterString) mkString ";"
      }

    exportPackages mkString ","
  }

  private def nameEqualsValue(parameters: List[Parameter]) = {
    parameters map { case Parameter(name, value) =>
      s"$name=${quote(value)}"
    }
  }

  def quote(s: String) = '"' + s + '"'
}
