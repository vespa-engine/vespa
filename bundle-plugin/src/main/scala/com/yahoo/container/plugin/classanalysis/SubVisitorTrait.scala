// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import collection.mutable

/**
 * A visitor that's run for sub construct of a class
 * and forwards all its imports the the owning ClassVisitor at the end.
 * @author  tonytv
 */
private trait SubVisitorTrait {
  val analyzeClassVisitor : AnalyzeClassVisitor

  val imports : ImportsSet = mutable.Set()

  def visitEnd() {
    analyzeClassVisitor.addImports(imports)
  }
}
