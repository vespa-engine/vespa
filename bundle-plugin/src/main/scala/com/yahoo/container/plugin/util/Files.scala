// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

import java.io.{FileOutputStream, File}
import com.yahoo.container.plugin.util.IO._

/**
 * @author  tonytv
 */
object Files {
  def allDescendantFiles(file: File): Stream[File] = {
    if (file.isFile)
      Stream(file)
    else if (file.isDirectory)
      file.listFiles().toStream.map(allDescendantFiles).flatten
    else
      Stream.empty
  }

  def withFileOutputStream[T](file: File)(f: FileOutputStream => T): T = {
    using(new FileOutputStream(file), readOnly = false)(f)
  }
}
