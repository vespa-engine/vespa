// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import scala.reflect.ClassTag
import java.io.File

/**
 * @author  tonytv
 */
object TestUtilities {
  def analyzeClass[T](implicit m: ClassTag[T]) =
    Analyze.analyzeClass(classFile(name(m)))

  def classFile(className : String) =
    new File("target/test-classes/" + className.replace('.', '/') + ".class")

  def name[T](implicit m: ClassTag[T]) = m.runtimeClass.getName
}
