// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey.xml

import scala.language.implicitConversions
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTestBase._
import com.yahoo.vespa.model.container.jersey.{JerseyHandler => ModelJerseyHandler, RestApiContext, RestApi}
import com.yahoo.config.model.builder.xml.test.DomBuilderTest
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTestBase
import MultipleRestApisTest._
import org.junit.Test
import scala.xml.Elem
import org.w3c.dom.Element
import com.yahoo.container.jdisc.JdiscBindingsConfig
import org.junit.Assert._
import org.hamcrest.CoreMatchers._
import com.yahoo.container.ComponentsConfig
import com.yahoo.component.ComponentId
import com.yahoo.container.di.config.JerseyBundlesConfig

/**
 * @author gjoranv
 * @since 5.11
 */

class MultipleRestApisTest extends ContainerModelBuilderTestBase {

  trait TestApp {
    createModel(root, restApiXml)

    val handler1 = getContainerComponentNested(ClusterId, HandlerId1).asInstanceOf[ModelJerseyHandler]
    val handler2 = getContainerComponentNested(ClusterId, HandlerId2).asInstanceOf[ModelJerseyHandler]
    val restApis = getContainerCluster(ClusterId).getRestApiMap
  }

  @Test
  def cluster_has_all_rest_apis() {
    new TestApp {
      assertThat(restApis.size(), is(2))
    }
  }

  @Test
  def rest_apis_have_path_as_component_id() {
    new TestApp {
      assertTrue(restApis.get(ComponentId.fromString(Path1)).isInstanceOf[RestApi])
      assertTrue(restApis.get(ComponentId.fromString(Path2)).isInstanceOf[RestApi])
    }
  }

  @Test
  def jersey_handler_has_correct_bindings() {
    new TestApp {
      assertThat(handler1, not(nullValue()))
      assertThat(handler1.getServerBindings, hasItems(HttpBinding1, HttpsBinding1))

      assertThat(handler2, not(nullValue()))
      assertThat(handler2.getServerBindings, hasItems(HttpBinding2, HttpsBinding2))
    }
  }

  @Test
  def jersey_bindings_are_included_in_config() {
    new TestApp {
      val config = root.getConfig(classOf[JdiscBindingsConfig], ClusterId)
      assertThat(config.handlers(HandlerId1).serverBindings(), hasItems(HttpBinding1, HttpsBinding1))
      assertThat(config.handlers(HandlerId2).serverBindings(), hasItems(HttpBinding2, HttpsBinding2))
    }
  }


  @Test
  def jersey_handler_for_each_rest_api_is_included_in_components_config() {
    new TestApp {
      val config = root.getConfig(classOf[ComponentsConfig], ClusterId)
      assertThat(config.toString, containsString(".id \"" + HandlerId1 + "\""))
      assertThat(config.toString, containsString(".id \"" + HandlerId2 + "\""))
    }
  }

  @Test
  def jersey_bundles_component_for_each_rest_api_is_included_in_components_config() {
    new TestApp {
      val config = root.getConfig(classOf[ComponentsConfig], ClusterId)
      assertThat(config.toString, containsString(".id \"" + RestApiContextId1 + "\""))
      assertThat(config.toString, containsString(".id \"" + RestApiContextId2 + "\""))
    }
  }

  @Test
  def each_rest_api_has_correct_bundle() {
    new TestApp {
      val restApiContext1 = restApis.get(ComponentId.fromString(Path1)).getContext
      val restApiContext2 = restApis.get(ComponentId.fromString(Path2)).getContext

      val bundlesConfig1 = root.getConfig(classOf[JerseyBundlesConfig], restApiContext1.getConfigId)
      assertThat(bundlesConfig1.toString, containsString("bundle1"))
      assertThat(bundlesConfig1.toString, not(containsString("bundle2")))

      val bundlesConfig2 = root.getConfig(classOf[JerseyBundlesConfig], restApiContext2.getConfigId)
      assertThat(bundlesConfig2.toString, containsString("bundle2"))
      assertThat(bundlesConfig2.toString, not(containsString("bundle1")))
    }
  }

}

object MultipleRestApisTest {
  val ClusterId = "container"

  val Path1 = "rest_1"
  val Path2 = "rest_2"
  val HttpBinding1 = "http://*/" + Path1 + "/*"
  val HttpsBinding1 = "https://*/" + Path1 + "/*"
  val HttpBinding2 = "http://*/" + Path2 + "/*"
  val HttpsBinding2 = "https://*/" + Path2 + "/*"

  val HandlerId1 = ModelJerseyHandler.CLASS + "-" + Path1
  val HandlerId2 = ModelJerseyHandler.CLASS + "-" + Path2
  val RestApiContextId1 = RestApiContext.CONTAINER_CLASS + "-" + Path1
  val RestApiContextId2 = RestApiContext.CONTAINER_CLASS + "-" + Path2

  val restApiXml =
    <container version="1.0" id={ClusterId}>
      <rest-api path={Path1}>
        <components bundle="bundle1" />
      </rest-api>

      <rest-api path={Path2}>
        <components bundle="bundle2" />
      </rest-api>
    </container>

  implicit def toDomElement(elem: Elem): Element = {
    DomBuilderTest.parse(elem.toString())
  }

}
