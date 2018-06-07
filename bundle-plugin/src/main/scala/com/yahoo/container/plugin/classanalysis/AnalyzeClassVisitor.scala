// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.objectweb.asm._
import com.yahoo.osgi.annotation.{ExportPackage, Version}
import collection.mutable

/**
 * Picks up classes used in class files.
 * @author  tonytv
 */
private class AnalyzeClassVisitor extends ClassVisitor(Opcodes.ASM6) with AnnotationVisitorTrait with AttributeVisitorTrait {
  private var name : String = null
  protected val imports : ImportsSet = mutable.Set()
  protected var exportPackageAnnotation: Option[ExportPackageAnnotation] = None


  override def visitAttribute(attribute: Attribute): Unit = super.visitAttribute(attribute)

  override def visitMethod(access: Int, name: String, desc: String, signature: String,
                           exceptions: Array[String]): MethodVisitor = {

    imports ++= (Type.getReturnType(desc) +: Type.getArgumentTypes(desc)).flatMap(getClassName)

    imports ++= Option(exceptions) getOrElse(Array()) flatMap internalNameToClassName

    AnalyzeSignatureVisitor.analyzeMethod(signature, this)
    new AnalyzeMethodVisitor(this)
  }

  override def visitField(access: Int, name: String, desc: String, signature: String, value: AnyRef): FieldVisitor = {
    imports ++= getClassName(Type.getType(desc)).toList

    AnalyzeSignatureVisitor.analyzeField(signature, this)
    new FieldVisitor(Opcodes.ASM6) with SubVisitorTrait with AttributeVisitorTrait with AnnotationVisitorTrait {
      val analyzeClassVisitor = AnalyzeClassVisitor.this

      override def visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor = super.visitAnnotation(desc, visible)
      override def visitAttribute(attribute: Attribute): Unit = super.visitAttribute(attribute)
      override def visitEnd(): Unit = super.visitEnd()
    }
  }

  override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]) {
    this.name = internalNameToClassName(name).get

    imports ++= (superName +: interfaces) flatMap internalNameToClassName
    AnalyzeSignatureVisitor.analyzeClass(signature, this)
  }

  override def visitInnerClass(name: String, outerName: String, innerName: String, access: Int) {}
  override def visitOuterClass(owner: String, name: String, desc: String) {}
  override def visitSource(source: String, debug: String) {}
  override def visitEnd() {}

  def addImports(imports: TraversableOnce[String]) {
    this.imports ++= imports
  }

  override def visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor = {
    if (Type.getType(desc).getClassName == classOf[ExportPackage].getName) {
      visitExportPackage()
    } else {
      super.visitAnnotation(desc, visible)
    }
  }

  def visitExportPackage(): AnnotationVisitor = {
    def defaultVersionValue[T](name: String) = classOf[Version].getMethod(name).getDefaultValue().asInstanceOf[T]

    new AnnotationVisitor(Opcodes.ASM6) {
      var major: Int = defaultVersionValue("major")
      var minor: Int = defaultVersionValue("minor")
      var micro: Int = defaultVersionValue("micro")
      var qualifier: String = defaultVersionValue("qualifier")

      override def visit(name: String, value: AnyRef) {
        def valueAsInt = value.asInstanceOf[Int]

        name match {
          case "major" => major = valueAsInt
          case "minor" => minor = valueAsInt
          case "micro" => micro = valueAsInt
          case "qualifier" => qualifier = value.asInstanceOf[String]
        }
      }

      override def visitEnd() {
        exportPackageAnnotation = Some(ExportPackageAnnotation(major, minor, micro, qualifier))
      }

      override def visitEnum(name: String, desc: String, value: String) {}
      override def visitArray(name: String): AnnotationVisitor = this
      override def visitAnnotation(name: String, desc: String): AnnotationVisitor = this
    }
  }

  def result = {
    assert(!imports.contains("int"))
    new ClassFileMetaData(name, imports.toSet, exportPackageAnnotation)
  }
}
