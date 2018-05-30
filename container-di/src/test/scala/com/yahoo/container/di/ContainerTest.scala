// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di

import com.yahoo.component.AbstractComponent
import com.yahoo.config.di.IntConfig
import com.yahoo.config.test.TestConfig
import com.yahoo.container.bundle.MockBundle
import com.yahoo.container.di.ContainerTest._
import com.yahoo.container.di.componentgraph.Provider
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.{SimpleComponent, SimpleComponent2}
import com.yahoo.container.di.componentgraph.core.ComponentNode.ComponentConstructorException
import com.yahoo.container.di.componentgraph.core.{ComponentGraph, Node}
import com.yahoo.container.di.config.RestApiContext
import org.hamcrest.CoreMatchers._
import org.junit.Assert._
import org.junit.{After, Before, Ignore, Test}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{existentials, postfixOps}
import scala.util.Try

/**
 * @author tonytv
 * @author gjoranv
 */
class ContainerTest {
  var dirConfigSource: DirConfigSource = _

  @Before def setup()  {
    dirConfigSource = new DirConfigSource("ContainerTest-")
  }

  @After def cleanup() {
    dirConfigSource.cleanup()
  }

  @Test
  def components_can_be_created_from_config() {
    writeBootstrapConfigs()
    dirConfigSource.writeConfig("test", """stringVal "myString" """)

    val container = newContainer(dirConfigSource)

    val component = createComponentTakingConfig(container.getNewConfigGraph())
    assertThat(component.config.stringVal(), is("myString"))

    container.shutdownConfigurer()
  }

  @Test
  def components_are_reconfigured_after_config_update_without_bootstrap_configs() {
    writeBootstrapConfigs()
    dirConfigSource.writeConfig("test", """stringVal "original" """)

    val container = newContainer(dirConfigSource)

    val componentGraph = container.getNewConfigGraph()
    val component = createComponentTakingConfig(componentGraph)

    assertThat(component.config.stringVal(), is("original"))

    // Reconfigure
    dirConfigSource.writeConfig("test", """stringVal "reconfigured" """)
    container.reloadConfig(2)

    val newComponentGraph = container.getNewConfigGraph(componentGraph)
    val component2 = createComponentTakingConfig(newComponentGraph)
    assertThat(component2.config.stringVal(), is("reconfigured"))

    container.shutdownConfigurer()
  }

  @Test
  def graph_is_updated_after_bootstrap_update() {
    dirConfigSource.writeConfig("test", """stringVal "original" """)
    writeBootstrapConfigs("id1")

    val container = newContainer(dirConfigSource)

    val graph = container.getNewConfigGraph()
    val component = createComponentTakingConfig(graph)
    assertThat(component.getId.toString, is("id1"))

    writeBootstrapConfigsWithMultipleComponents(Array(
      ("id1", classOf[ComponentTakingConfig]),
      ("id2", classOf[ComponentTakingConfig])))

    container.reloadConfig(2)
    val newGraph = container.getNewConfigGraph(graph)

    assertThat(ComponentGraph.getNode(newGraph, "id1"), notNullValue(classOf[Node]))
    assertThat(ComponentGraph.getNode(newGraph, "id2"), notNullValue(classOf[Node]))

    container.shutdownConfigurer()
  }

  //@Test TODO
  def deconstructor_is_given_guice_components() {
  }

  @Test
  def osgi_component_is_deconstructed_when_not_reused() {
    writeBootstrapConfigs("id1", classOf[DestructableComponent])

    val container = newContainer(dirConfigSource)

    val oldGraph = container.getNewConfigGraph()
    val componentToDestruct = oldGraph.getInstance(classOf[DestructableComponent])

    writeBootstrapConfigs("id2", classOf[DestructableComponent])
    container.reloadConfig(2)
    container.getNewConfigGraph(oldGraph)
    assertTrue(componentToDestruct.deconstructed)
  }

  @Ignore  // because logAndDie is impossible(?) to verify programmatically
  @Test
  def manually_verify_what_happens_when_first_graph_contains_component_that_throws_exception_in_ctor() {
    writeBootstrapConfigs("thrower", classOf[ComponentThrowingExceptionInConstructor])
    val container = newContainer(dirConfigSource)
    var currentGraph: ComponentGraph = null
    try {
      currentGraph = container.getNewConfigGraph()
      fail("Expected to log and die.")
    } catch {
      case _: Throwable => fail("Expected to log and die")
    }
  }

  @Test
  def previous_graph_is_retained_when_new_graph_contains_component_that_throws_exception_in_ctor() {
    val simpleComponentEntry = ComponentEntry("simpleComponent", classOf[SimpleComponent])

    writeBootstrapConfigs(Array(simpleComponentEntry))
    val container = newContainer(dirConfigSource)
    var currentGraph = container.getNewConfigGraph()

    val simpleComponent = currentGraph.getInstance(classOf[SimpleComponent])

    writeBootstrapConfigs("thrower", classOf[ComponentThrowingExceptionInConstructor])
    container.reloadConfig(2)
    try {
      currentGraph = container.getNewConfigGraph(currentGraph)
      fail("Expected exception")
    } catch {
      case _: ComponentConstructorException => // Expected, do nothing
      case _: Throwable => fail("Expected ComponentConstructorException")
    }
    assertEquals(1, currentGraph.generation)

    // Also verify that next reconfig is successful
    val componentTakingConfigEntry = ComponentEntry("componentTakingConfig", classOf[ComponentTakingConfig])
    dirConfigSource.writeConfig("test", """stringVal "myString" """)
    writeBootstrapConfigs(Array(simpleComponentEntry, componentTakingConfigEntry))
    container.reloadConfig(3)
    currentGraph = container.getNewConfigGraph(currentGraph)

    assertEquals(3, currentGraph.generation)
    assertSame(simpleComponent, currentGraph.getInstance(classOf[SimpleComponent]))
    assertNotNull(currentGraph.getInstance(classOf[ComponentTakingConfig]))
  }

  @Test
  def previous_graph_is_retained_when_new_graph_throws_exception_for_missing_config() {
    val simpleComponentEntry = ComponentEntry("simpleComponent", classOf[SimpleComponent])

    writeBootstrapConfigs(Array(simpleComponentEntry))
    val container = newContainer(dirConfigSource)
    var currentGraph = container.getNewConfigGraph()

    val simpleComponent = currentGraph.getInstance(classOf[SimpleComponent])

    writeBootstrapConfigs("thrower", classOf[ComponentThrowingExceptionForMissingConfig])
    dirConfigSource.writeConfig("test", """stringVal "myString" """)
    container.reloadConfig(2)
    try {
      currentGraph = container.getNewConfigGraph(currentGraph)
      fail("Expected exception")
    } catch {
      case _: IllegalArgumentException => // Expected, do nothing
      case _: Throwable => fail("Expected IllegalArgumentException")
    }
    assertEquals(1, currentGraph.generation)
  }

  @Test
  def runOnce_hangs_waiting_for_valid_config_after_invalid_config() {
    dirConfigSource.writeConfig("test", """stringVal "original" """)
    writeBootstrapConfigs("myId", classOf[ComponentTakingConfig])

    val container = newContainer(dirConfigSource)
    var currentGraph = container.getNewConfigGraph()

    writeBootstrapConfigs("thrower", classOf[ComponentThrowingExceptionForMissingConfig])
    container.reloadConfig(2)

    try {
      currentGraph = container.getNewConfigGraph(currentGraph)
      fail("expected exception")
    } catch {
      case e: Exception =>
    }

    val newGraph = Future {
      currentGraph = container.getNewConfigGraph(currentGraph)
      currentGraph
    }

    Try {
      Await.ready(newGraph, 1 second)
    } foreach { x => fail("Expected waiting for new config.") }


    writeBootstrapConfigs("myId2", classOf[ComponentTakingConfig])
    container.reloadConfig(3)

    assertNotNull(Await.result(newGraph, 5 minutes))
  }


  @Test
  def bundle_info_is_set_on_rest_api_context() {
    val clazz = classOf[RestApiContext]

    writeBootstrapConfigs("restApiContext", clazz)
    dirConfigSource.writeConfig("jersey-bundles", """bundles[0].spec "mock-entry-to-enforce-a-MockBundle" """)
    dirConfigSource.writeConfig("jersey-injection", """inject[0]" """)

    val container = newContainer(dirConfigSource)
    val componentGraph = container.getNewConfigGraph()

    val restApiContext = componentGraph.getInstance(clazz)
    assertNotNull(restApiContext)

    assertThat(restApiContext.getBundles.size, is(1))
    assertThat(restApiContext.getBundles.get(0).symbolicName, is(MockBundle.SymbolicName))
    assertThat(restApiContext.getBundles.get(0).version, is(MockBundle.BundleVersion))

    container.shutdownConfigurer()
   }

  @Test
  def restApiContext_has_all_components_injected() {
    new JerseyInjectionTest {
      assertFalse(restApiContext.getInjectableComponents.isEmpty)
      assertThat(restApiContext.getInjectableComponents.size(), is(2))

      container.shutdownConfigurer()
    }
  }

  // TODO: reuse injectedComponent as a named component when we support that
  trait JerseyInjectionTest {
    val restApiClass = classOf[RestApiContext]
    val injectedClass = classOf[SimpleComponent]
    val injectedComponentId = "injectedComponent"
    val anotherComponentClass = classOf[SimpleComponent2]
    val anotherComponentId = "anotherComponent"

    val componentsConfig: String =
      ComponentEntry(injectedComponentId, injectedClass).asConfig(0) + "\n" +
        ComponentEntry(anotherComponentId, anotherComponentClass).asConfig(1) + "\n" +
        ComponentEntry("restApiContext", restApiClass).asConfig(2) + "\n" +
        s"components[2].inject[0].id $injectedComponentId\n" +
        s"components[2].inject[1].id $anotherComponentId\n"

    val injectionConfig = s"""inject[1]
                             |inject[0].instance $injectedComponentId
                             |inject[0].forClass "${injectedClass.getName}"
                            """.stripMargin

    dirConfigSource.writeConfig("components", componentsConfig)
    dirConfigSource.writeConfig("bundles", "")
    dirConfigSource.writeConfig("jersey-bundles", """bundles[0].spec "mock-entry-to-enforce-a-MockBundle" """)
    dirConfigSource.writeConfig("jersey-injection", injectionConfig)

    val container = newContainer(dirConfigSource)
    val componentGraph = container.getNewConfigGraph()

    val restApiContext = componentGraph.getInstance(restApiClass)
  }

  case class ComponentEntry(componentId: String,  classId: Class[_]) {
    def asConfig(position: Int): String = {
      <config>
      |components[{position}].id "{componentId}"
      |components[{position}].classId "{classId.getName}"
      |components[{position}].configId "{dirConfigSource.configId}"
      </config>.text.stripMargin.trim
    }
  }

  def writeBootstrapConfigs(componentEntries: Array[ComponentEntry]) {
    dirConfigSource.writeConfig("bundles", "")
    dirConfigSource.writeConfig("components", """
      components[%s]
      %s
    """.format(componentEntries.length,
      componentEntries.zipWithIndex.map{ case (entry, index) => entry.asConfig(index) }.mkString("\n")))
  }

  def writeBootstrapConfigs(componentId: String = classOf[ComponentTakingConfig].getName,
                            classId: Class[_] = classOf[ComponentTakingConfig]) {

    writeBootstrapConfigs(Array(ComponentEntry(componentId, classId)))
  }

  def writeBootstrapConfigsWithMultipleComponents(idAndClass: Array[(String, Class[_])]) {
    writeBootstrapConfigs(idAndClass.map{case(id, classId) => ComponentEntry(id, classId)})
  }


  @Test
  def providers_are_destructed() {
    writeBootstrapConfigs("id1", classOf[DestructableProvider])

    val deconstructor = new ComponentDeconstructor {
      def deconstruct(component: AnyRef) {
        component match  {
          case c : AbstractComponent => c.deconstruct()
          case p : Provider[_] => p.deconstruct()
        }
      }
    }

    val container = newContainer(dirConfigSource, deconstructor)

    val oldGraph = container.getNewConfigGraph()
    val destructableEntity = oldGraph.getInstance(classOf[DestructableEntity])

    writeBootstrapConfigs("id2", classOf[DestructableProvider])
    container.reloadConfig(2)
    container.getNewConfigGraph(oldGraph)

    assertTrue(destructableEntity.deconstructed)
  }
}


object ContainerTest {
  class DestructableEntity {
    var deconstructed = false
  }

  class DestructableProvider extends Provider[DestructableEntity] {
    val instance = new DestructableEntity

    def get() = instance

    def deconstruct() {
      require(! instance.deconstructed)
      instance.deconstructed = true
    }
  }

  class ComponentTakingConfig(val config: TestConfig) extends AbstractComponent {
    require(config != null)
  }

  class ComponentThrowingExceptionInConstructor() {
    throw new RuntimeException("This component fails upon construction.")
  }

  class ComponentThrowingExceptionForMissingConfig(intConfig: IntConfig) extends AbstractComponent {
    fail("This component should never be created. Only used for tests where 'int' config is missing.")
  }

  class DestructableComponent extends AbstractComponent {
    var deconstructed = false
    override def deconstruct() {
      deconstructed = true
    }
  }

  class TestDeconstructor extends ComponentDeconstructor {
    def deconstruct(component: AnyRef) {
      component match {
        case vespaComponent: DestructableComponent => vespaComponent.deconstruct()
        case _ =>
      }
    }
  }

  private def newContainer(dirConfigSource: DirConfigSource,
                           deconstructor: ComponentDeconstructor = new TestDeconstructor()):
  Container = {
    new Container(new CloudSubscriberFactory(dirConfigSource.configSource), dirConfigSource.configId, deconstructor)
  }

  def createComponentTakingConfig(componentGraph: ComponentGraph): ComponentTakingConfig = {
    componentGraph.getInstance(classOf[ComponentTakingConfig])
  }

  def convertMap[K, V](map: java.util.Map[K, V]): Map[K, V] = map.asScala.toMap
}
