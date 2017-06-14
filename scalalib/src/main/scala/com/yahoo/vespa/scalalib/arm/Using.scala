// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.scalalib.arm

import scala.language.reflectiveCalls

/**
 * @author tonytv
 */
object Using {
  def using[RESOURCE <: { def close() }, RETURN](resource: RESOURCE)(f: RESOURCE => RETURN) = {
    try {
      f(resource)
    } finally {
      if (resource != null) resource.close()
    }
  }
}
