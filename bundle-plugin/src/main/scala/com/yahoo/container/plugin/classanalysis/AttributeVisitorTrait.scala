// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.objectweb.asm.{Type, Attribute}

/**
 * @author  tonytv
 */
private trait AttributeVisitorTrait {
  protected val imports: ImportsSet

  def visitAttribute(attribute: Attribute) {
    imports ++= getClassName(Type.getObjectType(attribute.`type`)).toList
  }
}
