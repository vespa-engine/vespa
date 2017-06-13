// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

import java.io.{Closeable, FileOutputStream, OutputStream, FileInputStream, File}
import util.control.Exception
import scala.Either

/** Utility methods relating to IO
 * @author  tonytv
 */
object IO {
  def withFileInputStream[T](file : File)(f : FileInputStream => T) = {
    using(new FileInputStream(file), readOnly = true)(f)
  }

  /**
   * Creates a new file and all it's parent directories,
   * and provides a file output stream to the file.
   *
   * Exceptions from closing have priority over exceptions from f.
   */
  def withFileOutputStream[T](file: File)(f: OutputStream => T) {
    makeDirectoriesRecursive(file.getParentFile)
    using(new FileOutputStream(file), readOnly = false )(f)
  }

  def makeDirectoriesRecursive(file: File) {
    if (!file.mkdirs() && !file.isDirectory) {
      throw new RuntimeException("Could not create directory " + file.getPath)
    }
  }

  def using[RESOURCE <: Closeable, T](resource : RESOURCE, readOnly : Boolean)(f : RESOURCE => T) : T  = {
    def catchPromiscuously = Exception.catchingPromiscuously(classOf[Throwable])

    val resultOrException =   catchPromiscuously either  f(resource)
    val closeException    =   Exception.allCatch either  resource.close()

    prioritizeFirstException(
      resultOrException,
      if (readOnly) Right(()) else closeException) fold (throw _, identity)
  }

  private def prioritizeFirstException[T](first: Either[Throwable, T], second: Either[Throwable, Unit]) =
    first fold ( Left(_), value => second fold ( Left(_), _ => Right(value) ) )
}
