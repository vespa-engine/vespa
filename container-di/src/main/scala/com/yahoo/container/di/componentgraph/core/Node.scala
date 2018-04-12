// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import java.util.logging.Logger

import com.google.inject.Key
import com.yahoo.component.ComponentId
import com.yahoo.container.di.ConfigKeyT
import com.yahoo.container.di.componentgraph.Provider
import com.yahoo.container.di.componentgraph.core.Node._
import com.yahoo.log.LogLevel.{DEBUG, SPAM}

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
abstract class Node(val componentId: ComponentId) {

  def instanceKey: Key[AnyRef]

  var instance : Option[AnyRef] = None

  var componentsToInject = List[Node]()

  /**
   * The components actually used by this node.
   * Consist of a subset of the injected nodes + subset of the global nodes.
   */
  def usedComponents: List[Node]

  protected def newInstance() : AnyRef

  def newOrCachedInstance() : AnyRef = {
    val inst = if (instance.isEmpty) {
      log.log(DEBUG, s"Creating new instance for component with ID $componentId")
      instance = Some(newInstance())
      instance.get
    } else {
      log.log(SPAM, s"Reusing instance for component with ID $componentId")
      instance.get
    }
    component(inst)
  }

  private def component(instance: AnyRef) = instance match {
    case provider: Provider[_] => provider.get().asInstanceOf[AnyRef]
    case other                 => other
  }

  def configKeys: Set[ConfigKeyT]

  def inject(component: Node) {
    componentsToInject ::= component
  }

  def injectAll(componentNodes: Traversable[ComponentNode]) {
    componentNodes.foreach(inject(_))
  }

  def instanceType: Class[_ <: AnyRef]
  def componentType: Class[_ <: AnyRef]

  override def equals(other: Any) = {
    other match {
      case that: Node =>
        getClass == that.getClass &&
          componentId == that.componentId &&
          instanceType == that.instanceType &&
          equalEdges(usedComponents, that.usedComponents)
      case _ => false
    }
  }

  def label: String

  def idAndType = {
    val className = instanceType.getName

    if (className == componentId.getName) s"'$componentId'"
    else s"'$componentId' of type '$className'"
  }

}

object Node {
  private val log = Logger.getLogger(classOf[Node].getName)

  def equalEdges(edges1: List[AnyRef], edges2: List[AnyRef]): Boolean = {
    def compare(objects: (AnyRef, AnyRef)): Boolean = {
      objects match {
        case (edge1: Node, edge2: Node) => equalEdge(edge1, edge2)
        case (o1, o2) => o1 == o2
      }
    }

    def equalEdge(e1: Node, e2: Node) =  e1.componentId == e2.componentId

    (edges1 zip edges2).forall(compare)
  }

  /**
   * @param identityObject  The identifying object that makes the Node unique
   */
  private[componentgraph]
  def syntheticComponentId(className: String, identityObject: AnyRef, namespace: ComponentId) = {
    val name = className + "_" + System.identityHashCode(identityObject)
    ComponentId.fromString(name).nestInNamespace(namespace)
  }


  def packageName(componentClass: Class[_]) = {
    def nullIfNotFound(index : Int) = if (index == -1) 0 else index

    val fullClassName = componentClass.getName
    fullClassName.substring(0, nullIfNotFound(fullClassName.lastIndexOf(".")))
  }
}
