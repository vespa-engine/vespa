// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import com.yahoo.component.{ComponentId, AbstractComponent}
import org.junit.Assert._
import org.hamcrest.CoreMatchers.{is, not, sameInstance, equalTo}
import com.yahoo.vespa.config.ConfigKey
import java.util.concurrent.Executor
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.{ExecutorProvider, SimpleComponent, SimpleComponent2}
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.{ComponentTakingConfig, ComponentTakingExecutor, singletonExecutorInjector}
import com.yahoo.container.di.makeClassCovariant
import org.junit.Test
import com.yahoo.config.subscription.ConfigGetter
import com.yahoo.config.test.TestConfig

/**
 * @author gjoranv
 * @author tonytv
 */
class ReuseComponentsTest {
  import ReuseComponentsTest._

  @Test
  def require_that_component_is_reused_when_componentNode_is_unmodified() {
    def reuseAndTest(classToRegister: Class[AnyRef], classToLookup: Class[AnyRef]) {
      val graph = buildGraphAndSetNoConfigs(classToRegister)
      val instance = getComponent(graph, classToLookup)

      val newGraph = buildGraphAndSetNoConfigs(classToRegister)
      newGraph.reuseNodes(graph)
      val instance2 = getComponent(newGraph, classToLookup)

      assertThat(instance2, sameInstance(instance))
    }

    reuseAndTest(classOf[SimpleComponent],  classOf[SimpleComponent])
    reuseAndTest(classOf[ExecutorProvider], classOf[Executor])
  }


  @Test(expected = classOf[IllegalStateException])
  def require_that_component_is_not_reused_when_class_is_changed() {
    val graph = buildGraphAndSetNoConfigs(classOf[SimpleComponent])
    val instance = getComponent(graph, classOf[SimpleComponent])

    val newGraph = buildGraphAndSetNoConfigs(classOf[SimpleComponent2])
    newGraph.reuseNodes(graph)
    val instance2 = getComponent(newGraph, classOf[SimpleComponent2])

    assertThat(instance2.getId, is(instance.getId))
    val throwsException = getComponent(newGraph, classOf[SimpleComponent])
  }

  @Test
  def require_that_component_is_not_reused_when_config_is_changed() {
    def setConfig(graph: ComponentGraph, config: String) {
      graph.setAvailableConfigs(
        Map(new ConfigKey(classOf[TestConfig], "component") ->
          ConfigGetter.getConfig(classOf[TestConfig], """raw: stringVal "%s" """.format(config))))
    }

    val componentClass = classOf[ComponentTakingConfig]

    val graph = buildGraph(componentClass)
    setConfig(graph, "oldConfig")
    val instance = getComponent(graph, componentClass)

    val newGraph = buildGraph(componentClass)
    setConfig(newGraph, "newConfig")
    newGraph.reuseNodes(graph)
    val instance2 = getComponent(newGraph, componentClass)

    assertThat(instance2, not(sameInstance(instance)))
  }

  @Test
  def require_that_component_is_not_reused_when_injected_component_is_changed() {
    import ComponentGraphTest.{ComponentTakingComponent, ComponentTakingConfig}

    def buildGraph(config: String) = {
      val graph = new ComponentGraph

      val rootComponent = mockComponentNode(classOf[ComponentTakingComponent], "root_component")

      val configId = "componentTakingConfigId"
      val injectedComponent = mockComponentNode(classOf[ComponentTakingConfig], "injected_component", configId)

      rootComponent.inject(injectedComponent)

      graph.add(rootComponent)
      graph.add(injectedComponent)

      graph.complete()
      graph.setAvailableConfigs(Map(new ConfigKey(classOf[TestConfig], configId) ->
        ConfigGetter.getConfig(classOf[TestConfig], """raw: stringVal "%s" """.format(config))))

      graph
    }

    val oldGraph = buildGraph(config="oldGraph")
    val oldInstance = getComponent(oldGraph, classOf[ComponentTakingComponent])

    val newGraph = buildGraph(config="newGraph")
    newGraph.reuseNodes(oldGraph)
    val newInstance = getComponent(newGraph, classOf[ComponentTakingComponent])

    assertThat(newInstance, not(sameInstance(oldInstance)))
  }

  @Test
  def require_that_component_is_not_reused_when_injected_component_registry_has_one_component_removed() {
    import ComponentGraphTest.ComponentTakingAllSimpleComponents

    def buildGraph(useBothInjectedComponents: Boolean) = {
      val graph = new ComponentGraph
      graph.add(mockComponentNode(classOf[ComponentTakingAllSimpleComponents], "root_component"))

      /* Below if-else has code duplication, but explicit ordering of the two components
       * was necessary to reproduce erroneous behaviour in ComponentGraph.reuseNodes that
       * occurred before ComponentRegistryNode got its own 'equals' implementation.
       */
      if (useBothInjectedComponents) {
        graph.add(mockComponentNode(classOf[SimpleComponent], "injected_component2"))
        graph.add(mockComponentNode(classOf[SimpleComponent], "injected_component1"))
      } else {
        graph.add(mockComponentNode(classOf[SimpleComponent], "injected_component1"))
      }

      graph.complete()
      graph.setAvailableConfigs(Map())
      graph
    }

    val oldGraph = buildGraph(useBothInjectedComponents = true)
    val oldSimpleComponentRegistry = getComponent(oldGraph, classOf[ComponentTakingAllSimpleComponents]).simpleComponents

    val newGraph = buildGraph(useBothInjectedComponents = false)
    newGraph.reuseNodes(oldGraph)
    val newSimpleComponentRegistry = getComponent(newGraph, classOf[ComponentTakingAllSimpleComponents]).simpleComponents

    assertThat(newSimpleComponentRegistry, not(sameInstance(oldSimpleComponentRegistry)))
  }

  @Test
  def require_that_injected_component_is_reused_even_when_dependent_component_is_changed() {
    import ComponentGraphTest.{ComponentTakingConfigAndComponent, SimpleComponent}

    def buildGraph(config: String) = {
      val graph = new ComponentGraph

      val configId = "componentTakingConfigAndComponent"
      val rootComponent = mockComponentNode(classOf[ComponentTakingConfigAndComponent], "root_component", configId)

      val injectedComponent = mockComponentNode(classOf[SimpleComponent], "injected_component")

      rootComponent.inject(injectedComponent)

      graph.add(rootComponent)
      graph.add(injectedComponent)

      graph.complete()
      graph.setAvailableConfigs(Map(new ConfigKey(classOf[TestConfig], configId) ->
        ConfigGetter.getConfig(classOf[TestConfig], """raw: stringVal "%s" """.format(config))))

      graph
    }

    val oldGraph = buildGraph(config="oldGraph")
    val oldInjectedComponent = getComponent(oldGraph, classOf[SimpleComponent])
    val oldDependentComponent = getComponent(oldGraph, classOf[ComponentTakingConfigAndComponent])

    val newGraph = buildGraph(config="newGraph")
    newGraph.reuseNodes(oldGraph)
    val newInjectedComponent = getComponent(newGraph, classOf[SimpleComponent])
    val newDependentComponent = getComponent(newGraph, classOf[ComponentTakingConfigAndComponent])

    assertThat(newDependentComponent, not(sameInstance(oldDependentComponent)))
    assertThat(newInjectedComponent, sameInstance(oldInjectedComponent))
  }

  @Test
  def require_that_node_depending_on_guice_node_is_reused() {
    def makeGraph = {
      val graph = new ComponentGraph
      graph.add(mockComponentNode(classOf[ComponentTakingExecutor], "dummyId"))
      graph.complete(singletonExecutorInjector)
      graph.setAvailableConfigs(Map())
      graph
    }

    val getComponentTakingExecutor = getComponent(_: ComponentGraph, classOf[ComponentTakingExecutor])

    val oldGraph = makeGraph
    getComponentTakingExecutor(oldGraph)  // Ensure creation of GuiceNode
    val newGraph = makeGraph
    newGraph.reuseNodes(oldGraph)
    assertThat(getComponentTakingExecutor(oldGraph), sameInstance(getComponentTakingExecutor(newGraph)))
  }

  @Test
  def require_that_node_equals_only_checks_first_level_components_to_inject() {

    def createNodeWithInjectedNodeWithInjectedNode(indirectlyInjectedComponentId: String): Node = {
      val targetComponent = mockComponentNode(classOf[SimpleComponent], "target")
      val directlyInjectedComponent = mockComponentNode(classOf[SimpleComponent], "directlyInjected")
      val indirectlyInjectedComponent = mockComponentNode(classOf[SimpleComponent], indirectlyInjectedComponentId)
      directlyInjectedComponent.inject(indirectlyInjectedComponent)
      targetComponent.inject(directlyInjectedComponent)

      completeNode(targetComponent)
      completeNode(directlyInjectedComponent)
      completeNode(indirectlyInjectedComponent)

      targetComponent
    }
    val targetNode1 = createNodeWithInjectedNodeWithInjectedNode("indirectlyInjected_1")
    val targetNode2 = createNodeWithInjectedNodeWithInjectedNode("indirectlyInjected_2")
    assertThat(targetNode1, equalTo(targetNode2))
  }

  private def completeNode(node: ComponentNode) {
    node.setArguments(Array())
    node.setAvailableConfigs(Map())
  }

  private def buildGraph(componentClass: Class[_ <: AnyRef]) = {
    val commonComponentId = "component"
    val g = new ComponentGraph
    g.add(mockComponentNode(componentClass, commonComponentId, configId = commonComponentId))
    g.complete()
    g
  }

  private def buildGraphAndSetNoConfigs(componentClass: Class[_ <: AnyRef]) = {
    val g = buildGraph(componentClass)
    g.setAvailableConfigs(Map())
    g
  }
}

object ReuseComponentsTest {

  def mockComponentNode(clazz: Class[_ <: AnyRef], componentId: String = "", configId: String="") =
    new ComponentNode(new ComponentId(componentId), configId, clazz)

  def getComponent[T](graph: ComponentGraph, clazz: Class[T]) = {
    graph.getInstance(clazz)
  }
}
