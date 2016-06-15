// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey.xml

import scala.language.implicitConversions
import RestApiTest._
import scala.xml.Elem
import scala.collection.JavaConverters._
import com.yahoo.config.model.builder.xml.test.DomBuilderTest
import org.w3c.dom.Element
import org.junit.{Ignore, Test}
import org.junit.Assert._
import com.yahoo.vespa.model.container.component.{Component, Handler}
import com.yahoo.vespa.model.container.jersey.{JerseyHandler => ModelJerseyHandler, RestApi, Jersey2Servlet, RestApiContext}
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTestBase
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTestBase._
import com.yahoo.container.ComponentsConfig
import org.hamcrest.CoreMatchers.{is, nullValue, notNullValue, not, containsString, hasItem, hasItems}
import org.hamcrest.Matchers.{empty, contains}
import com.yahoo.container.jdisc.JdiscBindingsConfig
import com.yahoo.container.di.config.{JerseyInjectionConfig, JerseyBundlesConfig}
import com.yahoo.container.config.jersey.JerseyInitConfig
import com.yahoo.component.ComponentId

/**
 * @author gjoranv
 * @since 5.5
 */
class RestApiTest extends ContainerModelBuilderTestBase {

  trait TestApp {
    createModel(root, restApiXml)
    root.validate()
    getContainerCluster(ClusterId).prepare()

    val restApi = getContainerCluster(ClusterId).getRestApiMap.values().iterator().next()
    val handler = getContainerComponentNested(ClusterId, HandlerId).asInstanceOf[ModelJerseyHandler]
    val context = restApi.getContext
  }

  @Test
  def jersey_handler_has_correct_bindings() {
    new TestApp {
      assertThat(handler, not(nullValue()))
      assertThat(handler.getServerBindings, hasItems(HttpBinding, HttpsBinding))
    }
  }

  @Test
  def jersey_bindings_are_included_in_config() {
    new TestApp {
      val config = root.getConfig(classOf[JdiscBindingsConfig], ClusterId)
      assertThat(config.handlers(HandlerId).serverBindings(), hasItems(HttpBinding, HttpsBinding))
    }
  }

  @Test
  def jersey_handler_has_correct_bundle_spec() {
    new TestApp {
      assertThat(handler.model.bundleInstantiationSpec.bundle.stringValue(), is(ModelJerseyHandler.BUNDLE))
    }
  }

  @Test
  def config_has_correct_jersey_mapping() {
    new TestApp {
      val config = root.getConfig(classOf[JerseyInitConfig], handler.getConfigId)
      assertThat(config.jerseyMapping, is(Path))
    }
  }

  @Test
  def resource_bundles_are_included_in_config() {
    new TestApp {
      val config = root.getConfig(classOf[JerseyBundlesConfig], context.getConfigId)

      assertThat(config.bundles.size, is(1))
      assertThat(config.bundles(0).spec, is("my-jersey-bundle:1.0"))
    }
  }

  @Test
  def packages_to_scan_are_included_in_config() {
    new TestApp {
      val config = root.getConfig(classOf[JerseyBundlesConfig], context.getConfigId)
      assertThat(config.bundles(0).packages, contains("com.yahoo.foo"))
    }
  }

  @Test
  def jersey_handler_is_included_in_components_config() {
    new TestApp {
      val config = root.getConfig(classOf[ComponentsConfig], ClusterId)
      assertThat(config.toString, containsString(".id \"" + HandlerId + "\""))
    }
  }

  @Test
  def restApiContext_is_included_in_components_config() {
    new TestApp {
      val config = root.getConfig(classOf[ComponentsConfig], ClusterId)
      assertThat(config.toString, containsString(".id \"" + RestApiContextId + "\""))
    }
  }

  @Test
  def all_non_restApi_components_are_injected_to_RestApiContext() {
    new TestApp {
      def restApiContextConfig(config: ComponentsConfig) =
        (for {
          component <- config.components.asScala
          if component.classId == RestApiContext.CONTAINER_CLASS
        } yield component).head

      val componentsConfig = root.getConfig(classOf[ComponentsConfig], ClusterId)

      val clusterChildrenComponentIds = getContainerCluster(ClusterId).getAllComponents.asScala.map(
        child => child.getComponentId).toSet

      val restApiChildrenComponentIds = restApi.getChildren.values.asScala.map(
        child => child.asInstanceOf[Component[_, _]].getComponentId).toSet

      //TODO: Review: replace with filtering against RestApiContext.isCycleGeneratingComponent
      val cycleInducingComponents = Set("com.yahoo.container.handler.observability.ApplicationStatusHandler") map ComponentId.fromString

      val expectedInjectedConfigIds = clusterChildrenComponentIds -- restApiChildrenComponentIds -- cycleInducingComponents

      val injectedConfigIds = restApiContextConfig(componentsConfig).inject.asScala.map(
        inject => ComponentId.fromString(inject.id()))

      // Verify that the two sets are equal. Split in two asserts to get decent failure messages.
      assertThat("Not all required components are injected",
                 (expectedInjectedConfigIds -- injectedConfigIds).asJavaCollection, empty[Any])
      assertThat("We inject some components that should not be injected",
                 (injectedConfigIds -- expectedInjectedConfigIds).asJavaCollection, empty[Any])
    }
  }

  @Ignore // TODO: use for naming components instead
  @Test
  def jdisc_components_can_be_injected() {
    new TestApp {
      val config = root.getConfig(classOf[JerseyInjectionConfig], context.getConfigId)
      assertThat(config.inject(0).instance(), is("injectedHandler"))
      assertThat(config.inject(0).forClass(), is("com.yahoo.handler.Handler"))
    }
  }

  @Ignore // TODO: use for naming a non-existent component instead
  @Test(expected = classOf[IllegalArgumentException])
  def injecting_non_existent_component() {
    val restApiXml =
      <container version="1.0" id={ClusterId}>
        <rest-api path={Path}>
          <components bundle="my-jersey-bundle:1.0" />
          <inject jdisc-component="non-existent" for-class="foo" />
        </rest-api>

      </container>

    createModel(root, restApiXml)
    root.validate()

  }

  @Test
  def legacy_syntax_should_produce_valid_model() {
    val legacyXml =
      <container version="1.0" >
        <handler id={ModelJerseyHandler.CLASS}>
          <binding>{HttpBinding}</binding>
          <config name="jdisc.jersey.jersey-handler">
            <jerseyMapping>jersey</jerseyMapping>
          </config>
        </handler>
      </container>

    createModel(root, legacyXml)

    val handler = getContainerComponent("container", ModelJerseyHandler.CLASS).asInstanceOf[Handler[_]]
    assertThat(handler, not(nullValue()))
    assertThat(handler.getServerBindings, hasItem(HttpBinding))

    val bindingsConfig = root.getConfig(classOf[JdiscBindingsConfig], ClusterId)
    assertThat(bindingsConfig.handlers(ModelJerseyHandler.CLASS).serverBindings(), hasItem(HttpBinding))
  }

}

object RestApiTest {
  val Path = "rest/api"
  val HttpBinding = "http://*/" + Path + "/*"
  val HttpsBinding = "https://*/" + Path + "/*"
  val HandlerId = ModelJerseyHandler.CLASS + "-" + RestApi.idFromPath(Path)
  val RestApiContextId = RestApiContext.CONTAINER_CLASS + "-" + RestApi.idFromPath(Path)
  val InjectedComponentId = "injectedHandler"

  val ClusterId = "container"

  val restApiXml =
    <container version="1.0" id={ClusterId} jetty="true">
      <rest-api path={Path}>
        <components bundle="my-jersey-bundle:1.0">
          <package>com.yahoo.foo</package>
        </components>
      </rest-api>

      <handler id={InjectedComponentId} />
    </container>

  implicit def toDomElement(elem: Elem): Element = {
    DomBuilderTest.parse(elem.toString())
  }

}
