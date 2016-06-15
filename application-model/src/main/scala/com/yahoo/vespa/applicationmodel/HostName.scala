// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel

import com.fasterxml.jackson.annotation.JsonValue

/**
 * @author <a href="mailto:bakksjo@yahoo-inc.com">Oyvind Bakksjo</a>
 */
case class HostName(s: String) {
  // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
  // Therefore, we use toString() as the JSON-producing method, which is really sad.
  @JsonValue
  override def toString(): String = s
}
