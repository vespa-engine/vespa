// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import java.util.logging.Logger

import com.yahoo.component.provider.ComponentRegistry
import com.yahoo.config.ConfigInstance

import java.lang.annotation.{Annotation => JavaAnnotation}
import java.lang.IllegalStateException
import com.yahoo.log.LogLevel

import collection.mutable
import annotation.tailrec

import com.yahoo.container.di.{ConfigKeyT, GuiceInjector}
import com.yahoo.container.di.componentgraph.Provider
import com.yahoo.vespa.config.ConfigKey
import com.google.inject.{Guice, ConfigurationException, Key, BindingAnnotation}
import net.jcip.annotations.NotThreadSafe

import com.yahoo.component.{AbstractComponent, ComponentId}
import java.lang.reflect.{TypeVariable, WildcardType, Method, ParameterizedType, Type}
import com.yahoo.container.di.removeStackTrace
import scala.util.Try
import scala.Some

/**
 * @author tonytv
 * @author gjoranv
 */
@NotThreadSafe
class ComponentGraph(val generation: Long = 0) {

  import ComponentGraph._

  private var nodesById = Map[ComponentId, Node]()

  private[di] def size =  nodesById.size

  def nodes = nodesById.values

  def add(component: Node) {
    require(!nodesById.isDefinedAt(component.componentId), "Multiple components with the same id " + component.componentId)
    nodesById += component.componentId -> component
  }

  def lookupGlobalComponent(key: Key[_]): Option[Node] = {
    require(key.getTypeLiteral.getType.isInstanceOf[Class[_]], "Type not supported " + key.getTypeLiteral)
    val clazz = key.getTypeLiteral.getRawType


    val components = matchingComponentNodes(nodesById.values, key)

    def singleNonProviderComponentOrThrow: Option[Node] = {
      val nonProviderComponents = components filter (c => !classOf[Provider[_]].isAssignableFrom(c.instanceType))
      nonProviderComponents.size match {
        case 0 => throw new IllegalStateException(s"Multiple global component providers for class '${clazz.getName}' found")
        case 1 => Some(nonProviderComponents.head.asInstanceOf[Node])
        case _ => throw new IllegalStateException(s"Multiple global components with class '${clazz.getName}' found")
      }
    }

    components.size match {
      case 0 => None
      case 1 => Some(components.head.asInstanceOf[Node])
      case _ => singleNonProviderComponentOrThrow
    }
  }

  def getInstance[T](clazz: Class[T]) : T = {
    getInstance(Key.get(clazz))
  }

  def getInstance[T](key: Key[T]) : T = {
    lookupGlobalComponent(key).
      // TODO: Combine exception handling with lookupGlobalComponent.
      getOrElse(throw new IllegalStateException("No global component with key '%s'  ".format(key.toString))).
      newOrCachedInstance().asInstanceOf[T]
  }

  private def componentNodes: Traversable[ComponentNode] =
    nodesOfType(nodesById.values, classOf[ComponentNode])

  private def componentRegistryNodes: Traversable[ComponentRegistryNode] =
    nodesOfType(nodesById.values, classOf[ComponentRegistryNode])

  private def osgiComponentsOfClass(clazz: Class[_]): Traversable[ComponentNode] = {
    componentNodes.filter(node => clazz.isAssignableFrom(node.componentType))
  }

  def complete(fallbackInjector: GuiceInjector = Guice.createInjector()) {
    def detectCycles = topologicalSort(nodesById.values.toList)

    componentNodes foreach {completeNode(_, fallbackInjector)}
    componentRegistryNodes foreach completeComponentRegistryNode
    detectCycles
  }

  def configKeys: Set[ConfigKeyT] = {
    nodesById.values.flatMap(_.configKeys).toSet
  }

  def setAvailableConfigs(configs: Map[ConfigKeyT, ConfigInstance]) {
    componentNodes foreach { _.setAvailableConfigs(configs) }
  }

  def reuseNodes(old: ComponentGraph) {
    def copyInstancesIfNodeEqual() {
      val commonComponentIds = nodesById.keySet & old.nodesById.keySet
      for (id <- commonComponentIds) {
        if (nodesById(id) == old.nodesById(id)) {
          nodesById(id).instance = old.nodesById(id).instance
        }
      }
    }
    def resetInstancesWithModifiedDependencies() {
      for {
        node <- topologicalSort(nodesById.values.toList)
        usedComponent <- node.usedComponents
      } {
        if (usedComponent.instance == None) {
          node.instance = None
        }
      }
    }

    copyInstancesIfNodeEqual()
    resetInstancesWithModifiedDependencies()
  }

  def allComponentsAndProviders = nodes map {_.instance.get}

  private def completeComponentRegistryNode(registry: ComponentRegistryNode) {
    registry.injectAll(osgiComponentsOfClass(registry.componentClass))
  }

  private def completeNode(node: ComponentNode, fallbackInjector: GuiceInjector) {
    try {
      val arguments = node.getAnnotatedConstructorParams.map(handleParameter(node, fallbackInjector, _))

      node.setArguments(arguments)
    } catch {
      case e : Exception => throw removeStackTrace(new RuntimeException(s"When resolving dependencies of ${node.idAndType}", e))
    }
  }

  private def handleParameter(node : Node,
                              fallbackInjector: GuiceInjector,
                              annotatedParameterType: (Type, Array[JavaAnnotation])): AnyRef =
  {
    def isConfigParameter(clazz : Class[_]) = classOf[ConfigInstance].isAssignableFrom(clazz)
    def isComponentRegistry(t : Type) = t == classOf[ComponentRegistry[_]]

    val (parameterType, annotations) = annotatedParameterType

    (parameterType match {
      case componentIdClass: Class[_] if componentIdClass == classOf[ComponentId] => node.componentId
      case configClass : Class[_] if isConfigParameter(configClass) => handleConfigParameter(node.asInstanceOf[ComponentNode], configClass)
      case registry : ParameterizedType if isComponentRegistry(registry.getRawType) => getComponentRegistry(registry.getActualTypeArguments.head)
      case clazz : Class[_] => handleComponentParameter(node, fallbackInjector, clazz, annotations)
      case other: ParameterizedType => sys.error(s"Injection of parameterized type $other is not supported.")
      case other => sys.error(s"Injection of type $other is not supported.")
    }).asInstanceOf[AnyRef]
  }


  def newComponentRegistryNode(componentClass: Class[AnyRef]): ComponentRegistryNode = {
    val registry = new ComponentRegistryNode(componentClass)
    add(registry) //TODO: don't mutate nodes here.
    registry
  }

  private def getComponentRegistry(componentType : Type) : ComponentRegistryNode = {
    val componentClass = componentType match {
      case wildCardType: WildcardType =>
        assert(wildCardType.getLowerBounds.isEmpty)
        assert(wildCardType.getUpperBounds.size == 1)
        wildCardType.getUpperBounds.head.asInstanceOf[Class[AnyRef]]
      case clazz: Class[AnyRef] => clazz
      case typeVariable: TypeVariable[_] =>
        throw new RuntimeException("Can't create ComponentRegistry of unknown type variable " + typeVariable)
    }

    componentRegistryNodes.find(_.componentClass == componentType).
      getOrElse(newComponentRegistryNode(componentClass))
  }

  def handleConfigParameter(node : ComponentNode,  clazz: Class[_]) : ConfigKeyT = {
    new ConfigKey(clazz.asInstanceOf[Class[ConfigInstance]], node.configId)
  }

  def getKey(clazz: Class[_], bindingAnnotation: Option[JavaAnnotation]) =
      bindingAnnotation.map(Key.get(clazz, _)).getOrElse(Key.get(clazz))

  private def handleComponentParameter(node: Node,
                                       fallbackInjector: GuiceInjector,
                                       clazz: Class[_],
                                       annotations: Array[JavaAnnotation]) : Node = {

    val bindingAnnotations = annotations.filter(isBindingAnnotation)
    val key = getKey(clazz, bindingAnnotations.headOption)

    def matchingGuiceNode(key: Key[_], instance: AnyRef): Option[GuiceNode] = {
      matchingNodes(nodesById.values, classOf[GuiceNode], key).
        filter(node => node.newOrCachedInstance eq instance). // TODO: assert that there is only one (after filter)
        headOption
    }

    def lookupOrCreateGlobalComponent: Node = {
      lookupGlobalComponent(key).getOrElse {
        val instance =
          try {
            log.log(LogLevel.DEBUG, "Trying the fallback injector to create" + messageForNoGlobalComponent(clazz, node))
            fallbackInjector.getInstance(key).asInstanceOf[AnyRef]
          } catch {
            case e: ConfigurationException =>
              throw removeStackTrace(new IllegalStateException(
                if (messageForMultipleClassLoaders(clazz).isEmpty)
                  "No global" + messageForNoGlobalComponent(clazz, node)
                else
                  messageForMultipleClassLoaders(clazz)))

          }
        matchingGuiceNode(key, instance).getOrElse {
          val node = new GuiceNode(instance, key.getAnnotation)
          add(node)
          node
        }
      }
    }

    if (bindingAnnotations.size > 1)
      sys.error("More than one binding annotation used in class '%s'".format(node.instanceType))

    val injectedNodesOfCorrectType = matchingComponentNodes(node.componentsToInject, key)
    injectedNodesOfCorrectType.size match {
      case 0 => lookupOrCreateGlobalComponent
      case 1 => injectedNodesOfCorrectType.head.asInstanceOf[Node]
      case _ => sys.error("Multiple components of type '%s' injected into component '%s'".format(clazz.getName, node.instanceType)) //TODO: !className for last parameter
    }
  }

}

object ComponentGraph {
  val log = Logger.getLogger(classOf[ComponentGraph].getName)

  def messageForNoGlobalComponent(clazz: Class[_], node: Node) =
    s" component of class ${clazz.getName} to inject into component ${node.idAndType}."

  def messageForMultipleClassLoaders(clazz: Class[_]): String = {
    val errMsg = "Class " + clazz.getName + " is provided by the framework, and cannot be embedded in a user bundle. " +
      "To resolve this problem, please refer to osgi-classloading.html#multiple-implementations in the documentation"

    (for {
      resolvedClass <- Try {Class.forName(clazz.getName, false, this.getClass.getClassLoader)}
      if resolvedClass != clazz
    } yield errMsg)
      .getOrElse("")
  }

  // For unit testing
  def getNode(graph: ComponentGraph, componentId: String): Node = {
    graph.nodesById(new ComponentId(componentId))
  }

  private def nodesOfType[T <: Node](nodes: Traversable[Node], clazz : Class[T]) : Traversable[T] = {
    nodes.collect {
      case node if clazz.isInstance(node) => clazz.cast(node)
    }
  }

  private def matchingComponentNodes(nodes: Traversable[Node], key: Key[_]) : Traversable[ComponentNode] = {
    matchingNodes(nodes, classOf[ComponentNode], key)
  }

  // Finds all nodes with a given nodeType and instance with given key
  private def matchingNodes[T <: Node](nodes: Traversable[Node], nodeType: Class[T],  key: Key[_]) : Traversable[T] = {
    val clazz = key.getTypeLiteral.getRawType
    val annotation = key.getAnnotation

    val filteredByClass = nodesOfType(nodes, nodeType) filter { node => clazz.isAssignableFrom(node.componentType) }
    val filteredByClassAndAnnotation = filteredByClass filter { node =>  annotation == node.instanceKey.getAnnotation }

    if (filteredByClass.size == 1) filteredByClass
    else if (filteredByClassAndAnnotation.size > 0) filteredByClassAndAnnotation
    else filteredByClass
  }

  // Returns true if annotation is a BindingAnnotation, e.g. com.google.inject.name.Named
  def isBindingAnnotation(annotation: JavaAnnotation) : Boolean = {
    def isBindingAnnotation(clazz: Class[_]) : Boolean = {
      val clazzOrSuperIsBindingAnnotation =
        (clazz.getAnnotation(classOf[BindingAnnotation]) != null) ||
          Option(clazz.getSuperclass).map(isBindingAnnotation).getOrElse(false)

      (clazzOrSuperIsBindingAnnotation /: clazz.getInterfaces.map(isBindingAnnotation))(_ || _)
    }
    isBindingAnnotation(annotation.getClass)
  }

  /**
   * The returned list is the nodes from the graph bottom-up.
   * @return A list where a earlier than b in the list implies that there is no path from a to b
   */
  def topologicalSort(nodes: List[Node]): List[Node] = {
    val numIncoming = mutable.Map[ComponentId, Int]().withDefaultValue(0)

    def forEachUsedComponent(nodes: Traversable[Node])(f: Node => Unit) {
      nodes.foreach(_.usedComponents.foreach(f))
    }

    def partitionByNoIncoming(nodes: List[Node]) =
      nodes.partition(node => numIncoming(node.componentId) == 0)

    @tailrec
    def sort(sorted: List[Node], unsorted: List[Node]) : List[Node] = {
      if (unsorted.isEmpty) {
        sorted
      } else {
        val (ready, notReady) = partitionByNoIncoming(unsorted)
        require(!ready.isEmpty, "There's a cycle in the graph.") //TODO: return cycle
        forEachUsedComponent(ready) { injectedNode => numIncoming(injectedNode.componentId) -= 1}
        sort(ready ::: sorted, notReady)
      }
    }

    forEachUsedComponent(nodes) { injectedNode => numIncoming(injectedNode.componentId) += 1 }
    sort(List(), nodes)
  }
}
