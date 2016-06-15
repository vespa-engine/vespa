// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

import java.util.jar.JarFile
import java.util.zip.{ZipFile, ZipEntry}
import IO.using
import java.io.{Closeable, InputStream, File}

/**
 * @author  tonytv
 */
object JarFiles {
  def withJarFile[T](file : File)(f : JarFile => T ) : T =
    using(new JarFile(file) with Closeable, readOnly = true)(f)

  def withInputStream[T](zipFile: ZipFile, zipEntry: ZipEntry)(f: InputStream => T): T =
    using(zipFile.getInputStream(zipEntry), readOnly = true)(f)

  def getManifest(jarFile : File) : Option[java.util.jar.Manifest] = {
    withJarFile(jarFile) { jar =>
      Option(jar.getManifest)
    }
  }
}
