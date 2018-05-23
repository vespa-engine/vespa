// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey

import javax.ws.rs.Path
import javax.ws.rs.ext.Provider

import org.objectweb.asm.{ClassVisitor, Opcodes, Type, AnnotationVisitor, ClassReader}


/**
 * @author tonytv
 */
class ResourceOrProviderClassVisitor private () extends ClassVisitor(Opcodes.ASM6) {
  private var className: String = null
  private var isPublic: Boolean = false
  private var isAbstract = false

  private var isInnerClass: Boolean = false
  private var isStatic: Boolean = false

  private var isAnnotated: Boolean = false

  def getJerseyClassName: Option[String] = {
    if (isJerseyClass) Some(getClassName)
    else None
  }

  def isJerseyClass: Boolean = {
    isAnnotated && isPublic && !isAbstract &&
      (!isInnerClass || isStatic)
  }

  def getClassName = {
    assert (className != null)
    Type.getObjectType(className).getClassName
  }

  override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]) {
    isPublic = ResourceOrProviderClassVisitor.isPublic(access)
    className = name
    isAbstract =  ResourceOrProviderClassVisitor.isAbstract(access)
  }

  override def visitInnerClass(name: String, outerName: String, innerName: String, access: Int) {
    assert (className != null)

    if (name == className) {
      isInnerClass = true
      isStatic = ResourceOrProviderClassVisitor.isStatic(access)
    }
  }

  override def visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor = {
    isAnnotated |= ResourceOrProviderClassVisitor.annotationClassDescriptors(desc)
    null
  }
}


object ResourceOrProviderClassVisitor {
  val annotationClassDescriptors = Set(classOf[Path], classOf[Provider]) map Type.getDescriptor

  def isPublic = isSet(Opcodes.ACC_PUBLIC) _
  def isStatic = isSet(Opcodes.ACC_STATIC) _
  def isAbstract = isSet(Opcodes.ACC_ABSTRACT) _

  private def isSet(bits: Int)(access: Int): Boolean = (access & bits) == bits

  def visit(classReader: ClassReader): ResourceOrProviderClassVisitor = {
    val visitor = new ResourceOrProviderClassVisitor
    classReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES)
    visitor
  }
}
