// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

/**
* @author  tonytv
*/
object Extractors {
  class ListOf[C](val c : Class[C]) {
    def unapply[X](xs : X) : Option[List[C]] = {
      xs match {
        case x :: xr if c.isInstance(x) => unapply(xr) map ( c.cast(x) :: _)
        case Nil => Some(Nil)
        case _ => None
      }
    }
  }
}
