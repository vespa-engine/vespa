// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import java.lang.reflect.{Constructor, InvocationTargetException, Modifier, ParameterizedType, Type}
import java.util.logging.Logger

import com.google.inject.Inject
import com.yahoo.component.{AbstractComponent, ComponentId}
import com.yahoo.config.ConfigInstance
import com.yahoo.container.di.componentgraph.Provider
import com.yahoo.container.di.componentgraph.core.ComponentNode._
import com.yahoo.container.di.componentgraph.core.Node.equalEdges
import com.yahoo.container.di.{ConfigKeyT, JavaAnnotation, createKey, makeClassCovariant, preserveStackTrace, removeStackTrace}
import com.yahoo.vespa.config.ConfigKey

import scala.language.postfixOps

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
class ComponentNode(componentId: ComponentId,
                    val configId: String,
                    clazz: Class[_ <: AnyRef],
                    val XXX_key: JavaAnnotation = null) // TODO expose key, not javaAnnotation
  extends Node(componentId)
{
  require(!isAbstract(clazz), "Can't instantiate abstract class " + clazz.getName)

  var arguments : Array[AnyRef] = _

  val constructor: Constructor[AnyRef] = bestConstructor(clazz)

  var availableConfigs: Map[ConfigKeyT, ConfigInstance] = null

  override val instanceKey = createKey(clazz, XXX_key)

  override val instanceType = clazz

  override def usedComponents: List[Node] = {
    require(arguments != null, "Arguments must be set first.")
    arguments.collect{case node: Node => node}.toList
  }

  override val componentType: Class[AnyRef] = {
    def allSuperClasses(clazz: Class[_], coll : List[Class[_]]) : List[Class[_]] = {
      if (clazz == null) coll
      else allSuperClasses(clazz.getSuperclass, clazz :: coll)
    }

    def allGenericInterfaces(clazz : Class[_]) = allSuperClasses(clazz, List()) flatMap (_.getGenericInterfaces)

    def isProvider = classOf[Provider[_]].isAssignableFrom(clazz)
    def providerComponentType = (allGenericInterfaces(clazz).collect {
      case t: ParameterizedType if t.getRawType == classOf[Provider[_]] => t.getActualTypeArguments.head
    }).head

    if (isProvider) providerComponentType.asInstanceOf[Class[AnyRef]] //TODO: Test what happens if you ask for something that isn't a class, e.g. a parametrized type.
    else            clazz.asInstanceOf[Class[AnyRef]]
  }

  def setArguments(arguments: Array[AnyRef]) {
    this.arguments = arguments
  }

  def cutStackTraceAtConstructor(throwable: Throwable): Throwable = {
    def takeUntilComponentNode(elements: Array[StackTraceElement]) =
      elements.takeWhile(_.getClassName != classOf[ComponentNode].getName)

    def dropToInitAtEnd(elements: Array[StackTraceElement]) =
      elements.reverse.dropWhile(_.getMethodName != "<init>").reverse

    val modifyStackTrace = takeUntilComponentNode _ andThen dropToInitAtEnd

    val dependencyInjectorStackTraceMarker = new StackTraceElement("============= Dependency Injection =============", "newInstance", null, -1)

    if (throwable != null && !preserveStackTrace) {
      throwable.setStackTrace(modifyStackTrace(throwable.getStackTrace) :+
        dependencyInjectorStackTraceMarker)

      cutStackTraceAtConstructor(throwable.getCause)
    }
    throwable
  }

  override protected def newInstance() : AnyRef = {
    assert (arguments != null, "graph.complete must be called before retrieving instances.")

    val actualArguments = arguments.map {
      case node: Node => node.newOrCachedInstance()
      case config: ConfigKeyT => availableConfigs(config.asInstanceOf[ConfigKeyT])
      case other => other
    }

    val instance =
      try {
        constructor.newInstance(actualArguments: _*)
      } catch {
        case e: InvocationTargetException =>
          throw removeStackTrace(ErrorOrComponentConstructorException(cutStackTraceAtConstructor(e.getCause), s"Error constructing $idAndType"))
      }

    initId(instance)
  }

  private def ErrorOrComponentConstructorException(cause: Throwable, message: String) : Throwable = {
    if (cause != null && cause.isInstanceOf[Error]) // don't convert Errors to RuntimeExceptions
      new Error(message, cause)
    else
      new ComponentConstructorException(message, cause)
  }

  private def initId(component: AnyRef) = {
    def checkAndSetId(c: AbstractComponent) {
      if (c.hasInitializedId && c.getId != componentId )
        throw new IllegalStateException(
          s"Component with id '$componentId' is trying to set its component id explicitly: '${c.getId}'. " +
            "This is not allowed, so please remove any call to super() in your component's constructor.")

      c.initId(componentId)
    }

    component match {
      case component: AbstractComponent => checkAndSetId(component)
      case other => ()
    }
    component
  }

  override def equals(other: Any) = {
    other match {
      case that: ComponentNode =>
        super.equals(that) &&
          equalEdges(arguments.toList, that.arguments.toList) &&
          usedConfigs == that.usedConfigs
    }
  }

  private def usedConfigs = {
    require(availableConfigs != null, "setAvailableConfigs must be called!")
    ( arguments collect {case c: ConfigKeyT => c} map (availableConfigs) ).toList
  }

  def getAnnotatedConstructorParams: Array[(Type, Array[JavaAnnotation])] = {
    constructor.getGenericParameterTypes zip constructor.getParameterAnnotations
  }

  def setAvailableConfigs(configs: Map[ConfigKeyT, ConfigInstance]) {
    require (arguments != null, "graph.complete must be called before graph.setAvailableConfigs.")
    availableConfigs = configs
  }

  override def configKeys = {
    configParameterClasses.map(new ConfigKey(_, configId)).toSet
  }


  private def configParameterClasses: Array[Class[ConfigInstance]] = {
    constructor.getGenericParameterTypes.collect {
      case clazz: Class[_] if classOf[ConfigInstance].isAssignableFrom(clazz) => clazz.asInstanceOf[Class[ConfigInstance]]
    }
  }

  override def label = {
    val configNames = configKeys.map(_.getName + ".def").toList

    (List(instanceType.getSimpleName, Node.packageName(instanceType)) ::: configNames).
      mkString("{", "|", "}")
  }

}

object ComponentNode {
  val log = Logger.getLogger(classOf[ComponentNode].getName)

  private def bestConstructor(clazz: Class[AnyRef]) = {
    val publicConstructors = clazz.getConstructors.asInstanceOf[Array[Constructor[AnyRef]]]

    def constructorAnnotatedWithInject = {
      publicConstructors filter {_.getAnnotation(classOf[Inject]) != null} match {
        case Array() => None
        case Array(single) => Some(single)
        case _ => throwComponentConstructorException("Multiple constructors annotated with inject in class " + clazz.getName)
      }
    }

    def constructorWithMostConfigParameters = {
      def isConfigInstance(clazz: Class[_]) = classOf[ConfigInstance].isAssignableFrom(clazz)

      publicConstructors match {
        case Array() => throwComponentConstructorException("No public constructors in class " + clazz.getName)
        case Array(single) => single
        case _ =>
          log.warning("Multiple public constructors found in class %s, there should only be one. ".format(clazz.getName) +
            "If more than one public constructor is needed, the primary one must be annotated with @Inject.")
          publicConstructors.
            sortBy(_.getParameterTypes.filter(isConfigInstance).size).
            last
      }
    }

    constructorAnnotatedWithInject getOrElse constructorWithMostConfigParameters
  }

  private def throwComponentConstructorException(message: String) =
    throw removeStackTrace(new ComponentConstructorException(message))

  class ComponentConstructorException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
    def this(message: String) = this(message, null)
  }

  def isAbstract(clazz: Class[_ <: AnyRef]) = Modifier.isAbstract(clazz.getModifiers)
}
