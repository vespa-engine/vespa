// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.objectweb.asm.{Opcodes, AnnotationVisitor, Type}

/**
 * Picks up classes used in annotations.
 * @author  tonytv
 */
private trait AnnotationVisitorTrait {
  protected val imports: ImportsSet

  def visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor = {
    imports ++= getClassName(Type.getType(desc)).toList

    visitAnnotationDefault()
  }

  def visitAnnotationDefault(): AnnotationVisitor =
    new AnnotationVisitor(Opcodes.ASM6) {
      override def visit(name: String, value: AnyRef) {}

      override def visitEnum(name: String, desc: String, value: String) {
        imports ++= getClassName(Type.getType(desc)).toList
      }

      override def visitArray(name: String): AnnotationVisitor = this

      override def visitAnnotation(name: String, desc: String): AnnotationVisitor = {
        imports ++= getClassName(Type.getType(desc)).toList
        this
      }

      override def visitEnd() {}
    }

  def visitParameterAnnotation(parameter: Int, desc: String, visible: Boolean): AnnotationVisitor =
    visitAnnotation(desc, visible)
}
