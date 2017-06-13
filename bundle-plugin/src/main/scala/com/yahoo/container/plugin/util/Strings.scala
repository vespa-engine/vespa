// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

 /**
 * @author  tonytv
 */
object Strings {
  def emptyStringTo(replacement: String)(s: String) = {
    if (s.isEmpty) replacement
    else s
  }

  def noneIfEmpty(s: String) = Option(s).filterNot(_.isEmpty)
}
