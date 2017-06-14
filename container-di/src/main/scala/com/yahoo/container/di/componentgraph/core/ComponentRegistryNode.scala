// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import com.yahoo.component.provider.ComponentRegistry
import com.yahoo.component.{ComponentId, Component}
import ComponentRegistryNode._
import com.google.inject.Key
import com.google.inject.util.Types
import Node.syntheticComponentId

/**
 * @author tonytv
 * @author gjoranv
 */
class ComponentRegistryNode(val componentClass : Class[AnyRef])
  extends Node(componentId(componentClass)) {

  def usedComponents = componentsToInject

  protected def newInstance() = {
    val registry = new ComponentRegistry[AnyRef]

    componentsToInject foreach { component =>
      registry.register(component.componentId, component.newOrCachedInstance())
    }

    registry
  }

  override val instanceKey =
    Key.get(Types.newParameterizedType(classOf[ComponentRegistry[_]], componentClass)).asInstanceOf[Key[AnyRef]]

  override val instanceType: Class[AnyRef] = instanceKey.getTypeLiteral.getRawType.asInstanceOf[Class[AnyRef]]
  override val componentType: Class[AnyRef] = instanceType

  override def configKeys = Set()

  override def equals(other: Any) = {
    other match {
      case that: ComponentRegistryNode =>
        componentId == that.componentId &&       // includes componentClass
          instanceType == that.instanceType &&
          equalEdges(usedComponents, that.usedComponents)
      case _ => false
    }
  }

  override def label =
    "{ComponentRegistry\\<%s\\>|%s}".format(componentClass.getSimpleName, Node.packageName(componentClass))
}

object ComponentRegistryNode {
  val componentRegistryNamespace = ComponentId.fromString("ComponentRegistry")

  def componentId(componentClass: Class[_]) = {
    syntheticComponentId(componentClass.getName, componentClass, componentRegistryNamespace)
  }

  def equalEdges(edges: List[Node], otherEdges: List[Node]): Boolean = {
    def compareEdges = {
      (sortByComponentId(edges) zip sortByComponentId(otherEdges)).
        forall(equalEdge)
    }

    def sortByComponentId(in: List[Node]) = in.sortBy(_.componentId)
    def equalEdge(edgePair: (Node, Node)): Boolean = edgePair._1.componentId == edgePair._2.componentId

    edges.size == otherEdges.size &&
      compareEdges
  }
}
