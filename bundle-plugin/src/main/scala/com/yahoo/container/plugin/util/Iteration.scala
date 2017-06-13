// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

 /**
 * @author  tonytv
 */
object Iteration {
  def toStream[T](enumeration: java.util.Enumeration[T]): Stream[T] = {
    if (enumeration.hasMoreElements)
      Stream.cons(enumeration.nextElement(), toStream(enumeration))
    else
      Stream.Empty
  }
}
