// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.observability

import com.yahoo.text.Utf8
import com.yahoo.io.IOUtils
import java.io.{IOException, InputStreamReader, InputStream}
import com.google.common.io.ByteStreams

/**
 * @author tonytv
 */

object Graphviz {

  @throws(classOf[IOException])
  def runDot(outputType: String, graph: String) = {
    val process = Runtime.getRuntime.exec(Array("/bin/sh", "-c",  "unflatten -l7 | dot -T" + outputType))
    process.getOutputStream.write(Utf8.toBytes(graph))
    process.getOutputStream.close()

    val result = ByteStreams.toByteArray(process.getInputStream)
    process.waitFor() match {
      case 0 => result
      case 127 => throw new RuntimeException("Couldn't find dot, please ensure that Graphviz is installed.")
      case _ => throw new RuntimeException("Failed running dot: " + readString(process.getErrorStream))
    }
  }

  private def readString(inputStream: InputStream): String = {
    IOUtils.readAll(new InputStreamReader(inputStream, "UTF-8"))
  }

}
