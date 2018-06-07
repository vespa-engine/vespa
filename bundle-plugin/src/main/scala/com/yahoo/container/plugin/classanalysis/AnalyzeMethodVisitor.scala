// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.objectweb.asm._

/**
 * Picks up classes used in method bodies.
 * @author  tonytv
 */
private class AnalyzeMethodVisitor(val analyzeClassVisitor : AnalyzeClassVisitor)
  extends MethodVisitor(Opcodes.ASM6) with AnnotationVisitorTrait with AttributeVisitorTrait with SubVisitorTrait {


  override def visitParameterAnnotation(parameter: Int, desc: String, visible: Boolean): AnnotationVisitor = super.visitParameterAnnotation(parameter, desc, visible)
  override def visitAnnotationDefault(): AnnotationVisitor = super.visitAnnotationDefault()
  override def visitAttribute(attribute: Attribute): Unit = super.visitAttribute(attribute)
  override def visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor = super.visitAnnotation(desc, visible)
  override def visitEnd(): Unit = super.visitEnd()

  override def visitMultiANewArrayInsn(desc: String, dims: Int) {
    imports ++= getClassName(Type.getType(desc)).toList
  }


  override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
    imports ++= internalNameToClassName(owner)
    imports ++= Type.getArgumentTypes(desc).flatMap(getClassName)
    imports ++= getClassName(Type.getReturnType(desc))
  }

  override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
    imports ++= internalNameToClassName(owner) ++ getClassName(Type.getType(desc)).toList

  }

  override def visitTypeInsn(opcode: Int, `type` : String) {
    imports ++= internalNameToClassName(`type`)
  }

  override def visitTryCatchBlock(start: Label, end: Label, handler: Label, `type` : String) {
    if (`type` != null) //null means finally block
      imports ++= internalNameToClassName(`type`)
  }

  override def visitLocalVariable(name: String, desc: String, signature: String, start: Label, end: Label, index: Int) {
    imports += Type.getType(desc).getClassName
  }

  override def visitLdcInsn(constant: AnyRef) {
    constant match {
      case typeConstant: Type =>  imports ++= getClassName(typeConstant)
      case _ =>
    }
  }

  override def visitInvokeDynamicInsn(name: String, desc: String, bootstrapMethod: Handle, bootstrapMethodArgs: AnyRef*) {
    bootstrapMethodArgs.foreach {
      case typeConstant: Type =>
        imports ++= getClassName(typeConstant)
      case handle: Handle =>
        imports ++= internalNameToClassName(handle.getOwner)
        imports ++= Type.getArgumentTypes(desc).flatMap(getClassName)
      case _ : Number =>
      case _ : String =>
      case other => throw new AssertionError(s"Unexpected type ${other.getClass} with value '$other'")
    }
  }

  override def visitMaxs(maxStack: Int, maxLocals: Int) {}
  override def visitLineNumber(line: Int, start: Label) {}
  //only for debugging
  override def visitLookupSwitchInsn(dflt: Label, keys: Array[Int], labels: Array[Label]) {}


  override def visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels: Label*): Unit = super.visitTableSwitchInsn(min, max, dflt, labels: _*)
  override def visitIincInsn(`var` : Int, increment: Int) {}
  override def visitLabel(label: Label) {}
  override def visitJumpInsn(opcode: Int, label: Label) {}
  override def visitVarInsn(opcode: Int, `var` : Int) {}
  override def visitIntInsn(opcode: Int, operand: Int) {}
  override def visitInsn(opcode: Int) {}
  override def visitFrame(`type` : Int, nLocal: Int, local: Array[AnyRef], nStack: Int, stack: Array[AnyRef]) {}
  override def visitCode() {}
}




