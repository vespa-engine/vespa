// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import com.yahoo.container.plugin.util.Strings

/**
 * @author  tonytv
 */
case class ExportPackageAnnotation(major: Int, minor: Int, micro: Int, qualifier: String) {
  requireNonNegative(major, "major")
  requireNonNegative(minor, "minor")
  requireNonNegative(micro, "micro")
  require(qualifier.matches("""(\p{Alpha}|\p{Digit}|_|-)*"""),
    exportPackageError("qualifier must follow the format (alpha|digit|'_'|'-')* but was '%s'.".format(qualifier)))


  private def requireNonNegative(i: Int, fieldName: String) {
    require(i >= 0, exportPackageError("%s must be non-negative but was %d.".format(fieldName, i)))
  }

  private def exportPackageError(s: String) = "ExportPackage anntotation: " + s

  def osgiVersion : String = (List(major, minor, micro) ++ Strings.noneIfEmpty(qualifier)).mkString(".")
}
