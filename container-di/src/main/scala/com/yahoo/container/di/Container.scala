// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di

import com.yahoo.container.di.ConfigRetriever.{ComponentsConfigs, BootstrapConfigs}
import com.yahoo.container.di.componentgraph.core.{JerseyNode, ComponentGraph, ComponentNode}
import com.yahoo.container.di.config.{RestApiContext, SubscriberFactory}
import Container._

import scala.collection.JavaConversions._
import scala.math.max
import com.yahoo.config._
import com.yahoo.vespa.config.ConfigKey
import java.util.IdentityHashMap
import java.util.logging.Logger
import com.yahoo.container.bundle.BundleInstantiationSpecification
import com.google.inject.{Injector, Guice}
import com.yahoo.container.{BundlesConfig, ComponentsConfig}


/**
 *
 * @author gjoranv
 * @author tonytv
 */
class Container(
                 subscriberFactory: SubscriberFactory,
                 configId: String,
                 componentDeconstructor: ComponentDeconstructor,
                 osgi: Osgi = new Osgi {}
                 )
{
  val bundlesConfigKey    = new ConfigKey(classOf[BundlesConfig], configId)
  val componentsConfigKey = new ConfigKey(classOf[ComponentsConfig], configId)

  var configurer = new ConfigRetriever(Set(bundlesConfigKey, componentsConfigKey), subscriberFactory.getSubscriber(_))
  var previousConfigGeneration = -1L
  var leastGeneration = -1L

  @throws(classOf[InterruptedException])
  def runOnce(
               oldGraph: ComponentGraph = new ComponentGraph,
               fallbackInjector: GuiceInjector = Guice.createInjector()): ComponentGraph = {

    def deconstructObsoleteComponents(oldGraph: ComponentGraph, newGraph: ComponentGraph) {
      val oldComponents = new IdentityHashMap[AnyRef, AnyRef]()
      oldGraph.allComponentsAndProviders foreach(oldComponents.put(_, null))
      newGraph.allComponentsAndProviders foreach(oldComponents.remove(_))
      oldComponents.keySet foreach(componentDeconstructor.deconstruct(_))
    }

    try {
      //TODO: wrap user exceptions.
      val newGraph = createNewGraph(oldGraph, fallbackInjector)
      newGraph.reuseNodes(oldGraph)
      constructComponents(newGraph)
      deconstructObsoleteComponents(oldGraph, newGraph)
      newGraph
    } catch {
      case e : Throwable =>
        invalidateGeneration()
        throw e
    }
  }

  private def invalidateGeneration() {
    leastGeneration = max(configurer.getComponentsGeneration, configurer.getBootstrapGeneration) + 1
  }

  final def createNewGraph(graph: ComponentGraph = new ComponentGraph,
                           fallbackInjector: Injector): ComponentGraph = {

    val snapshot = configurer.getConfigs(graph.configKeys, leastGeneration)
    log.fine("""createNewGraph:
      graph.configKeys = %s
      graph.generation = %s
      snapshot = %s
    """.format(graph.configKeys, graph.generation, snapshot))

    val preventTailRecursion =
      snapshot match {
        case BootstrapConfigs(configs) if getBootstrapGeneration > previousConfigGeneration =>
          installBundles(configs)
          createNewGraph(
            createComponentsGraph(configs, getBootstrapGeneration,fallbackInjector),
            fallbackInjector)
        case BootstrapConfigs(_)        =>
          createNewGraph(graph, fallbackInjector)
        case ComponentsConfigs(configs) =>
          createAndConfigureComponentsGraph(configs, fallbackInjector)
      }

    preventTailRecursion
  }


  def getBootstrapGeneration: Long = {
    configurer.getBootstrapGeneration
  }

  def getComponentsGeneration: Long = {
    configurer.getComponentsGeneration
  }

  private def createAndConfigureComponentsGraph[T](
                                                    componentsConfigs: Map[ConfigKeyT, ConfigInstance],
                                                    fallbackInjector: Injector): ComponentGraph = {

    val componentGraph = createComponentsGraph(componentsConfigs, getComponentsGeneration, fallbackInjector)
    componentGraph.setAvailableConfigs(componentsConfigs)
    componentGraph
  }

  def injectNodes(config: ComponentsConfig, graph: ComponentGraph) {
    for {
      component <- config.components()
      inject <- component.inject()
    } {
      def getNode = ComponentGraph.getNode(graph, _: String)

      //TODO: Support inject.name()
      getNode(component.id()).inject(getNode(inject.id()))
    }

  }

  def installBundles(configsIncludingBootstrapConfigs: Map[ConfigKeyT, ConfigInstance]) {
    val bundlesConfig = getConfig(bundlesConfigKey, configsIncludingBootstrapConfigs)
    osgi.useBundles(bundlesConfig.bundle())
  }

  private def createComponentsGraph[T](
                                        configsIncludingBootstrapConfigs: Map[ConfigKeyT, ConfigInstance],
                                        generation: Long,
                                        fallbackInjector: Injector): ComponentGraph = {

    previousConfigGeneration = generation

    val graph = new ComponentGraph(generation)

    val componentsConfig = getConfig(componentsConfigKey, configsIncludingBootstrapConfigs)
    addNodes(componentsConfig, graph)
    injectNodes(componentsConfig, graph)

    graph.complete(fallbackInjector)
    graph
  }

  def addNodes[T](componentsConfig: ComponentsConfig, graph: ComponentGraph) {
    def isRestApiContext(clazz: Class[_]) = classOf[RestApiContext].isAssignableFrom(clazz)
    def asRestApiContext(clazz: Class[_]) = clazz.asInstanceOf[Class[RestApiContext]]

    for (config : ComponentsConfig.Components <- componentsConfig.components) {
      val specification = bundleInstatiationSpecification(config)
      val componentClass = osgi.resolveClass(specification)

      val componentNode =
        if (isRestApiContext(componentClass))
          new JerseyNode(specification.id, config.configId(), asRestApiContext(componentClass), osgi)
        else
          new ComponentNode(specification.id, config.configId(), componentClass)

      graph.add(componentNode)
    }
  }

  private def constructComponents(graph: ComponentGraph) {
    graph.nodes foreach (_.newOrCachedInstance())
  }

  def shutdown(graph: ComponentGraph, deconstructor: ComponentDeconstructor) {
    shutdownConfigurer()
    if (graph != null)
      deconstructAllComponents(graph, deconstructor)
  }

  def shutdownConfigurer() {
    configurer.shutdown()
  }

  // Reload config manually, when subscribing to non-configserver sources
  def reloadConfig(generation: Long) {
    subscriberFactory.reloadActiveSubscribers(generation)
  }

  def deconstructAllComponents(graph: ComponentGraph, deconstructor: ComponentDeconstructor) {
    graph.allComponentsAndProviders foreach(deconstructor.deconstruct(_))
  }

}

object Container {
  val log = Logger.getLogger(classOf[Container].getName)

  def getConfig[T <: ConfigInstance](key: ConfigKey[T], configs: Map[ConfigKeyT, ConfigInstance]) : T = {
    key.getConfigClass.cast(configs.getOrElse(key.asInstanceOf[ConfigKeyT], sys.error("Missing config " + key)))
  }

  def bundleInstatiationSpecification(config: ComponentsConfig.Components) =
    BundleInstantiationSpecification.getFromStrings(config.id(), config.classId(), config.bundle())
}
