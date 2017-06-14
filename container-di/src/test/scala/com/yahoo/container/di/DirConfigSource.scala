// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di

import java.io.{FileOutputStream, OutputStream, File}
import DirConfigSource._
import java.util.Random
import org.junit.rules.TemporaryFolder
import com.yahoo.config.subscription.{ConfigSource, ConfigSourceSet}

// TODO: Make this a junit rule. Does not yet work. Look out for junit updates
//       (@Rule def configSourceRule  = dirConfigSource)

/**
 * @author tonytv
 * @author gjoranv
 */
class DirConfigSource(val testSourcePrefix: String) {
  private val tempFolder = createTemporaryFolder()

  val configSource : ConfigSource = new ConfigSourceSet(testSourcePrefix + new Random().nextLong)

  def writeConfig(name: String, contents: String) {
    val file = new File(tempFolder.getRoot, name + ".cfg")
    if (!file.exists())
      file.createNewFile()

    printFile(file, contents + "\n")
  }

  def configId = "dir:" + tempFolder.getRoot.getPath

  def cleanup() {
    tempFolder.delete()
  }
}

private object DirConfigSource {
  def printFile(f: File, content: String) {
    var out: OutputStream = new FileOutputStream(f)
    out.write(content.getBytes("UTF-8"))
    out.close()
  }

  def createTemporaryFolder() = {
    val folder = new TemporaryFolder
    folder.create()
    folder
  }
}
