// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import org.junit.Test
import org.junit.Assert._
import org.hamcrest.CoreMatchers.{is, sameInstance, equalTo, not, containsString}

import com.yahoo.component.provider.ComponentRegistry
import com.google.inject.name.{Names, Named}
import com.yahoo.component.{ComponentId, AbstractComponent}
import org.hamcrest.Matcher
import com.yahoo.vespa.config.ConfigKey
import com.yahoo.config.{ConfigInstance}
import com.yahoo.config.test.{TestConfig, Test2Config}
import java.util.concurrent.{Executors, Executor}
import com.google.inject.{Guice, Key, AbstractModule, Inject, Provider=>GuiceProvider}
import com.yahoo.container.di._
import com.yahoo.config.subscription.ConfigGetter
import com.yahoo.container.di.config.{JerseyInjectionConfig, JerseyBundlesConfig, RestApiContext}
import com.yahoo.container.di.componentgraph.Provider

/**
 * @author gjoranv
 * @author tonytv
 */
class ComponentGraphTest {
  import ComponentGraphTest._

  private def keyAndConfig[T <: ConfigInstance](clazz: Class[T], configId: String): (ConfigKey[T], T) = {
    val key = new ConfigKey(clazz, configId)
    key -> ConfigGetter.getConfig(key.getConfigClass, key.getConfigId.toString)
  }

  @Test
  def component_taking_config_can_be_instantiated() {
    val componentGraph = new ComponentGraph
    val configId = "raw:stringVal \"test-value\""
    val componentNode = mockComponentNode(classOf[ComponentTakingConfig], configId)

    componentGraph.add(componentNode)
    componentGraph.complete()
    componentGraph.setAvailableConfigs(Map(keyAndConfig(classOf[TestConfig], configId)))

    val instance = componentGraph.getInstance(classOf[ComponentTakingConfig])
    assertNotNull(instance)
    assertThat(instance.config.stringVal(), is("test-value"))
  }

  @Test
  def component_can_be_injected_into_another_component() {
    val injectedComponent = mockComponentNode(classOf[SimpleComponent])
    val targetComponent = mockComponentNode(classOf[ComponentTakingComponent])
    targetComponent.inject(injectedComponent)

    val destroyGlobalLookupComponent = mockComponentNode(classOf[SimpleComponent])

    val componentGraph = new ComponentGraph
    componentGraph.add(injectedComponent)
    componentGraph.add(targetComponent)
    componentGraph.add(destroyGlobalLookupComponent)
    componentGraph.complete()


    val instance = componentGraph.getInstance(classOf[ComponentTakingComponent])
    assertNotNull(instance)
  }

  @Test
  def all_components_of_a_type_can_be_injected() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.add(mockComponentNode(classOf[SimpleDerivedComponent]))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingAllSimpleComponents]))
    componentGraph.complete()

    val instance = componentGraph.getInstance(classOf[ComponentTakingAllSimpleComponents])
    assertThat(instance.simpleComponents.allComponents().size(), is(3))
  }

  @Test
  def empty_component_registry_can_be_injected() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[ComponentTakingAllSimpleComponents]))
    componentGraph.complete()

    val instance = componentGraph.getInstance(classOf[ComponentTakingAllSimpleComponents])
    assertThat(instance.simpleComponents.allComponents().size(), is(0))
  }

  @Test
  def component_registry_with_wildcard_upper_bound_can_be_injected() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.add(mockComponentNode(classOf[SimpleDerivedComponent]))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingAllSimpleComponentsUpperBound]))
    componentGraph.complete()

    val instance = componentGraph.getInstance(classOf[ComponentTakingAllSimpleComponentsUpperBound])
    assertThat(instance.simpleComponents.allComponents().size(), is(2))
  }

  @Test(expected = classOf[RuntimeException])
  def require_exception_when_injecting_registry_with_unknown_type_variable() {
    val clazz = classOf[ComponentTakingAllComponentsWithTypeVariable[_]]

    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.add(mockComponentNode(classOf[SimpleDerivedComponent]))
    componentGraph.add(mockComponentNode(clazz))
    componentGraph.complete()

    componentGraph.getInstance(clazz)
  }

  @Test
  def components_are_shared() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.complete()

    val instance1 = componentGraph.getInstance(classOf[SimpleComponent])
    val instance2 = componentGraph.getInstance(classOf[SimpleComponent])
    assertThat(instance1, sameInstance(instance2))
  }

  @Test
  def singleton_components_can_be_injected() {
    val componentGraph = new ComponentGraph
    val configId = """raw:stringVal "test-value" """

    componentGraph.add(mockComponentNode(classOf[ComponentTakingComponent]))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingConfig], configId))
    componentGraph.add(mockComponentNode(classOf[SimpleComponent2]))
    componentGraph.complete()
    componentGraph.setAvailableConfigs(Map(keyAndConfig(classOf[TestConfig], configId)))

    val instance = componentGraph.getInstance(classOf[ComponentTakingComponent])
    assertThat(instance.injectedComponent.asInstanceOf[ComponentTakingConfig].config.stringVal(), is("test-value"))
  }

  @Test(expected = classOf[RuntimeException])
  def require_error_when_multiple_components_match_a_singleton_dependency() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[SimpleDerivedComponent]))
    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingComponent]))
    componentGraph.complete()
  }

  @Test
  def named_component_can_be_injected() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.add(mockComponentNode(classOf[SimpleComponent], key = Names.named("named-test")))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingNamedComponent]))
    componentGraph.complete()
  }

  @Test
  def config_keys_can_be_retrieved() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[ComponentTakingConfig], configId = """raw:stringVal "component1" """""))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingConfig], configId = """raw:stringVal "component2" """""))
    componentGraph.add(new ComponentRegistryNode(classOf[ComponentTakingConfig]))
    componentGraph.complete()

    val configKeys = componentGraph.configKeys
    assertThat(configKeys.size, is(2))

    configKeys.foreach{ key =>
      assertThat(key.getConfigClass, equalTo(classOf[TestConfig]))
      assertThat(key.getConfigId.toString, containsString("component"))
    }
  }

  @Test
  def providers_can_be_instantiated() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[ExecutorProvider]))
    componentGraph.complete()

    assertNotNull(componentGraph.getInstance(classOf[Executor]))
  }

  @Test
  def providers_can_be_inherited() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[DerivedExecutorProvider]))
    componentGraph.complete()

    assertNotNull(componentGraph.getInstance(classOf[Executor]))
  }

  @Test
  def providers_can_deliver_a_new_instance_for_each_component() {
    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNode(classOf[NewIntProvider]))
    componentGraph.complete()

    val instance1 = componentGraph.getInstance(classOf[Int])
    val instance2 = componentGraph.getInstance(classOf[Int])
    assertThat(instance1, not(equalTo(instance2)))
  }

  @Test
  def providers_can_be_injected_explicitly() {
    val componentGraph = new ComponentGraph

    val componentTakingExecutor = mockComponentNode(classOf[ComponentTakingExecutor])
    val executorProvider = mockComponentNode(classOf[ExecutorProvider])
    componentTakingExecutor.inject(executorProvider)

    componentGraph.add(executorProvider)
    componentGraph.add(mockComponentNode(classOf[ExecutorProvider]))

    componentGraph.add(componentTakingExecutor)

    componentGraph.complete()
    assertNotNull(componentGraph.getInstance(classOf[ComponentTakingExecutor]))
  }

  @Test
  def global_providers_can_be_injected() {
    val componentGraph = new ComponentGraph

    componentGraph.add(mockComponentNode(classOf[ComponentTakingExecutor]))
    componentGraph.add(mockComponentNode(classOf[ExecutorProvider]))
    componentGraph.add(mockComponentNode(classOf[IntProvider]))
    componentGraph.complete()

    assertNotNull(componentGraph.getInstance(classOf[ComponentTakingExecutor]))
  }

  @Test(expected = classOf[RuntimeException])
  def throw_if_multiple_global_providers_exist(): Unit = {
    val componentGraph = new ComponentGraph

    componentGraph.add(mockComponentNode(classOf[ExecutorProvider]))
    componentGraph.add(mockComponentNode(classOf[ExecutorProvider]))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingExecutor]))
    componentGraph.complete()
  }

  @Test
  def provider_is_not_used_when_component_of_provided_class_exists() {
    val componentGraph = new ComponentGraph

    componentGraph.add(mockComponentNode(classOf[SimpleComponent]))
    componentGraph.add(mockComponentNode(classOf[SimpleComponentProviderThatThrows]))
    componentGraph.add(mockComponentNode(classOf[ComponentTakingComponent]))
    componentGraph.complete()

    val injectedComponent = componentGraph.getInstance(classOf[ComponentTakingComponent]).injectedComponent
    assertNotNull(injectedComponent)
  }

  //TODO: move
  @Test
  def check_if_annotation_is_a_binding_annotation() {
    import ComponentGraph.isBindingAnnotation

    assertTrue(isBindingAnnotation(Names.named("name")))
    assertFalse(isBindingAnnotation(classOf[Named].getAnnotations.head))
  }

  @Test
  def cycles_gives_exception() {
    val componentGraph = new ComponentGraph

    def mockNode = mockComponentNode(classOf[ComponentCausingCycle])

    val node1 = mockNode
    val node2 = mockNode

    node1.inject(node2)
    node2.inject(node1)

    componentGraph.add(node1)
    componentGraph.add(node2)

    try {
      componentGraph.complete()
      fail("Cycle exception expected.")
    } catch {
      case e : Throwable => assertThat(e.getMessage, containsString("cycle"))
    }
  }

  @Test(expected = classOf[IllegalArgumentException])
  def abstract_classes_are_rejected() {
    new ComponentNode(ComponentId.fromString("Test"), "", classOf[AbstractClass])
  }

  @Test
  def inject_constructor_is_preferred() {
    assertThatComponentCanBeCreated(classOf[ComponentWithInjectConstructor])
  }

  @Test
  def constructor_with_most_parameters_is_preferred() {
    assertThatComponentCanBeCreated(classOf[ComponentWithMultipleConstructors])
  }

  def assertThatComponentCanBeCreated(clazz: Class[AnyRef]) {
    val componentGraph = new ComponentGraph
    val configId = """raw:stringVal "dummy" """"

    componentGraph.add(mockComponentNode(clazz, configId))
    componentGraph.complete()

    componentGraph.setAvailableConfigs(Map(
      keyAndConfig(classOf[TestConfig], configId),
      keyAndConfig(classOf[Test2Config], configId)))

    assertNotNull(componentGraph.getInstance(clazz))
  }

  @Test
  def require_fallback_to_child_injector() {
    val componentGraph = new ComponentGraph

    componentGraph.add(mockComponentNode(classOf[ComponentTakingExecutor]))

    componentGraph.complete(singletonExecutorInjector)
    assertNotNull(componentGraph.getInstance(classOf[ComponentTakingExecutor]))
  }

  @Test
  def child_injector_can_inject_multiple_instances_for_same_key() {
    def executorProvider() = Executors.newSingleThreadExecutor()

    val (graphSize, executorA, executorB)  = buildGraphWithChildInjector(executorProvider)

    assertThat(graphSize, is(4))
    assertThat(executorA, not(sameInstance(executorB)))
  }

  @Test
  def components_injected_via_child_injector_can_be_shared() {
    val commonExecutor = Executors.newSingleThreadExecutor()
    val (graphSize, executorA, executorB) = buildGraphWithChildInjector(() => commonExecutor)

    assertThat(graphSize, is(3))
    assertThat(executorA, sameInstance(executorB))
  }

  def buildGraphWithChildInjector(executorProvider: () => Executor) = {
    val childInjector = Guice.createInjector(new AbstractModule {
      override def configure() {
        bind(classOf[Executor]).toProvider(new GuiceProvider[Executor] {
          def get() = executorProvider()
        })
      }
    })

    val componentGraph = new ComponentGraph

    def key(name: String) = Key.get(classOf[ComponentTakingExecutor], Names.named(name))
    val keyA = key("A")
    val keyB = key("B")

    componentGraph.add(mockComponentNode(keyA))
    componentGraph.add(mockComponentNode(keyB))

    componentGraph.complete(childInjector)

    (componentGraph.size, componentGraph.getInstance(keyA).executor, componentGraph.getInstance(keyB).executor)
  }

  @Test
  def providers_can_be_reused() {
    def createGraph() = {
      val graph = new ComponentGraph()
      graph.add(mockComponentNodeWithId(classOf[ExecutorProvider], "dummyId"))
      graph.complete()
      graph.setAvailableConfigs(Map())
      graph
    }

    val oldGraph = createGraph()
    val executor = oldGraph.getInstance(classOf[Executor])

    val newGraph = createGraph()
    newGraph.reuseNodes(oldGraph)

    val newExecutor = newGraph.getInstance(classOf[Executor])
    assertThat(executor, sameInstance(newExecutor))
  }

  @Test
  def component_id_can_be_injected() {
    val componentId: String = "myId:1.2@namespace"

    val componentGraph = new ComponentGraph
    componentGraph.add(mockComponentNodeWithId(classOf[ComponentTakingComponentId], componentId))
    componentGraph.complete()

    assertThat(componentGraph.getInstance(classOf[ComponentTakingComponentId]).componentId,
      is(ComponentId.fromString(componentId)))
  }

  @Test
  def rest_api_context_can_be_instantiated() {
    val configId = """raw:"" """

    val clazz = classOf[RestApiContext]
    val jerseyNode = new JerseyNode(uniqueComponentId(clazz.getName), configId, clazz, new Osgi {})

    val componentGraph = new ComponentGraph
    componentGraph.add(jerseyNode)
    componentGraph.complete()
    componentGraph.setAvailableConfigs(Map(keyAndConfig(classOf[JerseyBundlesConfig], configId),
                                           keyAndConfig(classOf[JerseyInjectionConfig], configId)))

    val restApiContext = componentGraph.getInstance(clazz)
    assertNotNull(restApiContext)
    assertThat(restApiContext.getBundles.size, is(0))
  }

}

//Note that all Components must be defined in a static context,
//otherwise their constructor will take the outer class as the first parameter.
object ComponentGraphTest {
  var counter = 0


  class SimpleComponent extends AbstractComponent
  class SimpleComponent2 extends AbstractComponent
  class SimpleDerivedComponent extends SimpleComponent

  class ComponentTakingConfig(val config: TestConfig) extends SimpleComponent {
    require(config != null)
  }

  class ComponentTakingComponent(val injectedComponent: SimpleComponent) extends AbstractComponent {
    require(injectedComponent != null)
  }

  class ComponentTakingConfigAndComponent(val config: TestConfig, val injectedComponent: SimpleComponent) extends AbstractComponent {
    require(config != null)
    require(injectedComponent != null)
  }

  class ComponentTakingAllSimpleComponents(val simpleComponents: ComponentRegistry[SimpleComponent]) extends AbstractComponent {
    require(simpleComponents != null)
  }

  class ComponentTakingAllSimpleComponentsUpperBound(val simpleComponents: ComponentRegistry[_ <: SimpleComponent])
    extends AbstractComponent {

    require(simpleComponents != null)
  }

  class ComponentTakingAllComponentsWithTypeVariable[COMPONENT <: AbstractComponent](val simpleComponents: ComponentRegistry[COMPONENT])
    extends AbstractComponent {

    require(simpleComponents != null)
  }

  class ComponentTakingNamedComponent(@Named("named-test") injectedComponent: SimpleComponent) extends AbstractComponent {
    require(injectedComponent != null)
  }

  class ComponentCausingCycle(component: ComponentCausingCycle) extends AbstractComponent

  class SimpleComponentProviderThatThrows extends Provider[SimpleComponent] {
    def get() = throw new AssertionError("Should never be called.")
    def deconstruct() {}
  }

  class ExecutorProvider extends Provider[Executor] {
    val executor = Executors.newSingleThreadExecutor()
    def get() = executor
    def deconstruct() { /*TODO */ }
  }

  class DerivedExecutorProvider extends ExecutorProvider

  class IntProvider extends Provider[java.lang.Integer] {
    def get() = throw new AssertionError("Should never be called.")
    def deconstruct() {}
  }

  class NewIntProvider extends Provider[Integer] {
    var i: Int = 0
    def get() = {
      i += 1
      i
    }
    def deconstruct() {}
  }

  class ComponentTakingExecutor(val executor: Executor) extends AbstractComponent {
    require(executor != null)
  }

  class ComponentWithInjectConstructor private () {
    def this(c: TestConfig, c2: Test2Config) = { this(); sys.error("Should not be called") }
    @Inject
    def this(c: Test2Config) = { this() }
  }

  class ComponentWithMultipleConstructors private (dummy : Int) {
    def this(c: TestConfig, c2: Test2Config) = { this(0); }

    def this() = { this(0); sys.error("Should not be called") }
    def this(c: Test2Config) = { this() }
  }

  class ComponentTakingComponentId(val componentId: ComponentId)

  def uniqueComponentId(className: String): ComponentId = {
    counter += 1
    ComponentId.fromString(className + counter)
  }

  def mockComponentNode(key: Key[_ <: AnyRef]): Node =
    mockComponentNode(key.getTypeLiteral.getRawType.asInstanceOf[Class[AnyRef]], key=key.getAnnotation)

  def mockComponentNode(clazz: Class[_ <: AnyRef], configId: String = "", key: JavaAnnotation = null): Node =
    new ComponentNode(uniqueComponentId(clazz.getName), configId, clazz, key)

  def mockComponentNodeWithId(clazz: Class[_ <: AnyRef], componentId: String,  configId: String = "", key: JavaAnnotation = null): Node =
    new ComponentNode(ComponentId.fromString(componentId), configId, clazz, key)

  val singletonExecutorInjector = Guice.createInjector(new AbstractModule {
    override def configure() {
      bind(classOf[Executor]).toInstance(Executors.newSingleThreadExecutor())
    }
  })

  implicit def makeMatcherCovariant[T, U >: T](matcher: Matcher[T]) : Matcher[U] = matcher.asInstanceOf[Matcher[U]]

  abstract class AbstractClass
}

