// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin

import org.objectweb.asm.Type
import collection.mutable

package object classanalysis {
  type ImportsSet = mutable.Set[String]

  def internalNameToClassName(internalClassName: String) : Option[String] = {
    internalClassName match {
      case null => None
      case _ => getClassName(Type.getObjectType(internalClassName))
    }
  }

  def getClassName(aType: Type): Option[String] = {
    import Type._

    aType.getSort match {
      case ARRAY => getClassName(aType.getElementType)
      case OBJECT => Some(aType.getClassName)
      case _ => None
    }
  }
}

