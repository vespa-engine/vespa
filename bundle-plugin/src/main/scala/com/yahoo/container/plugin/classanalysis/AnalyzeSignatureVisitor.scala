// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.{SignatureReader, SignatureVisitor}


/**
 * @author tonytv
 */

private class AnalyzeSignatureVisitor(val analyzeClassVisitor: AnalyzeClassVisitor)
  extends SignatureVisitor(Opcodes.ASM6)
  with SubVisitorTrait {


  override def visitEnd(): Unit = super.visitEnd()

  override def visitClassType(className: String) {
    imports ++= internalNameToClassName(className)
  }

  override def visitFormalTypeParameter(name: String) {}

  override def visitClassBound() = this

  override def visitInterfaceBound() = this

  override def visitSuperclass() = this

  override def visitInterface() = this

  override def visitParameterType() = this

  override def visitReturnType() = this

  override def visitExceptionType() = this

  override def visitBaseType(descriptor: Char) {}

  override def visitTypeVariable(name: String) {}

  override def visitArrayType() = this

  override def visitInnerClassType(name: String) {}

  override def visitTypeArgument() {}

  override def visitTypeArgument(wildcard: Char) = this
}


object AnalyzeSignatureVisitor {
  def analyzeClass(signature: String, analyzeClassVisitor: AnalyzeClassVisitor) {
    if (signature != null) {
      new SignatureReader(signature).accept(new AnalyzeSignatureVisitor(analyzeClassVisitor))
    }
  }

  def analyzeMethod(signature: String, analyzeClassVisitor: AnalyzeClassVisitor)  {
    analyzeClass(signature, analyzeClassVisitor)
  }

  def analyzeField(signature: String, analyzeClassVisitor: AnalyzeClassVisitor) {
    if (signature != null)
      new SignatureReader(signature).acceptType(new AnalyzeSignatureVisitor(analyzeClassVisitor))
  }
}
