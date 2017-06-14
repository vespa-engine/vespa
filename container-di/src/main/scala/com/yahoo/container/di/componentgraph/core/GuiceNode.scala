// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import com.yahoo.container.di.{JavaAnnotation, createKey}
import com.yahoo.component.ComponentId
import Node.syntheticComponentId
import GuiceNode._

/**
 * @author tonytv
 * @author gjoranv
 */
final class GuiceNode(myInstance: AnyRef,
                      annotation: JavaAnnotation) extends Node(componentId(myInstance)) {

  override def configKeys = Set()

  override val instanceKey = createKey(myInstance.getClass, annotation)
  override val instanceType = myInstance.getClass
  override val componentType = instanceType


  override def usedComponents = List()

  override protected def newInstance() = myInstance

  override def inject(component: Node) {
    throw new UnsupportedOperationException("Illegal to inject components to a GuiceNode!")
  }

  override def label =
    "{{%s|Guice}|%s}".format(instanceType.getSimpleName, Node.packageName(instanceType))
}

object GuiceNode {
  val guiceNamespace = ComponentId.fromString("Guice")

  def componentId(instance: AnyRef) = {
    syntheticComponentId(instance.getClass.getName, instance, guiceNamespace)
  }
}
