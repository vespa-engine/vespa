// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.objectweb.asm._
import java.io.{InputStream, File}
import com.yahoo.container.plugin.util.IO.withFileInputStream

/**
 * Main entry point for class analysis
 * @author  tonytv
 */
object Analyze {
  def analyzeClass(classFile : File) : ClassFileMetaData = {
    try {
      withFileInputStream(classFile) { fileInputStream =>
        analyzeClass(fileInputStream)
      }
    } catch {
      case e : RuntimeException => throw new RuntimeException("An error occurred when analyzing " + classFile.getPath, e)
    }
  }

  def analyzeClass(inputStream : InputStream) : ClassFileMetaData = {
    val visitor = new AnalyzeClassVisitor()
    new ClassReader(inputStream).accept(visitor, ClassReader.SKIP_DEBUG)
    visitor.result
  }
}
