// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml


import org.junit.Test
import scala.xml.{PrettyPrinter, Elem}

import ManhattanContainerModelBuilderTest._
import com.yahoo.config.model.test.MockRoot
import org.apache.commons.io.IOUtils
import com.yahoo.vespa.model.container.ContainerCluster
import com.yahoo.vespa.model.container.component.{Component, AccessLogComponent}
import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import com.yahoo.config.model.producer.AbstractConfigProducer
import com.yahoo.osgi.provider.model.ComponentModel
import org.junit.Assert.{assertThat, assertNotNull}
import org.hamcrest.CoreMatchers.is
import com.yahoo.container.handler.VipStatusHandler
import com.yahoo.config.model.builder.xml.XmlHelper.getDocumentBuilder
import com.yahoo.vespa.model.container.search.searchchain.FederationSearcherTest
import com.yahoo.container.jdisc.config.HttpServerConfig
import com.yahoo.config.model.deploy.DeployState

import scala.language.reflectiveCalls

/**
 * @author tonytv
 */
class ManhattanContainerModelBuilderTest {

  val emptyJDiscElement = <jdisc version="1.0" />

  @Test
  def multiple_access_logs_configured() {
    val container = buildManhattanContainer(
      <jdisc version="1.0">
        <accesslog type="yapache" fileNamePattern="myPattern" />
        <accesslog type="vespa"   fileNamePattern="myPattern" />
      </jdisc>)
  }

  @Test
  def status_html_and_akamai_handlers_configured() {
    val container = buildManhattanContainer(emptyJDiscElement)

    val vipStatusComponents = getComponentsWithModelClass[VipStatusHandler](container)
    val ids = vipStatusComponents map { _.model.getComponentId.getName }

    assertThat(ids.toSet, is(Set("status.html-status-handler", "akamai-status-handler")))
  }

  @Test
  def http_server_added_automatically() {
    val container = buildManhattanContainer(emptyJDiscElement)

    assertThat(((container.getHttp.getHttpServer != null) && (container.getHttp.getHttpServer.getConnectorFactories.size() == 1)), is(true))
    assertThat(container.getHttp.getHttpServer.getConnectorFactories.head.getListenPort, is(httpPort))
  }

  @Test
  def only_the_first_http_server_is_kept() {
    val container = buildManhattanContainer(
      <jdisc version="1.0">
        <http>
          <server id="server1" port="123" />
          <server id="server2" port="456" />
        </http>
      </jdisc>)

    assertThat(((container.getHttp.getHttpServer != null) && (container.getHttp.getHttpServer.getConnectorFactories.size() == 1)), is(true))
    assertThat(container.getHttp.getHttpServer.getComponentId.getName, is("jdisc-jetty"))
    assertThat(container.getHttp.getHttpServer.getConnectorFactories.head.getName, is("server1"))
    assertThat(container.getHttp.getHttpServer.getConnectorFactories.head.getListenPort, is(httpPort))
  }

  @Test
  def filters_and_bindings_are_preserved() {
    val container = buildManhattanContainer(
      <jdisc version="1.0">
        <http>
          <filtering>
            <filter id="my-filter" />
            <request-chain id="my-chain">
              <filter id="my-filter" />
              <binding>http://*:123/my-binding</binding>
            </request-chain>
          </filtering>
          <server id="server1" port="123" />
        </http>
      </jdisc>)

    val binding = container.getHttp.getBindings.head
    assertThat(binding.filterId.getName, is("my-chain"))
    assertThat(binding.binding, is("http://*:123/my-binding"))

    val filterChains = container.getHttp.getFilterChains
    assertNotNull("Missing filter", filterChains.componentsRegistry().getComponent("my-filter"))
    assertNotNull("Missing chain",  filterChains.allChains().getComponent("my-chain"))
  }
}

object ManhattanContainerModelBuilderTest {
  type ACP = AbstractConfigProducer[_]
  type COMPONENT = Component[_ <: ACP, _ <: ComponentModel]

  val httpPort = 9876

  def getComponents[T <: COMPONENT](cluster: ContainerCluster)(implicit tag: ClassTag[T]): Iterable[T] = {
    fixType(cluster.getComponentsMap.values()) collect  { case c: T => c }
  }

  def getComponentsWithModelClass[T <: AnyRef](cluster: ContainerCluster)(implicit tag: ClassTag[T]) = {
    val className = tag.runtimeClass.getName
    fixType(cluster.getAllComponents) filter { _.model.getClassId.getName == className }
  }

  def modelClassIdMatches(name: String): PartialFunction[COMPONENT, COMPONENT] = {
    case c: COMPONENT if c.model.getClassId.getName == name => c
  }

  def fixType(components: java.util.Collection[_ <: Component[_, _]]): java.util.Collection[COMPONENT] =
    components.asInstanceOf[java.util.Collection[COMPONENT]]

  def buildManhattanContainer(elem: Elem) = {
    val root = new MockRoot()
    val containerModel = new ManhattanContainerModelBuilder(httpPort).build(DeployState.createTestState(), null, root, domElement(elem))
    root.freezeModelTopology()
    containerModel.getCluster()
  }

  def xmlStringBuilder(elem: Elem) = {
    val printer = new PrettyPrinter(240, 2)
    val builder = new StringBuilder
    builder.append("<?xml version='1.0' encoding='utf-8' ?>\n")
    printer.format(elem, builder)
    builder
  }

  def domElement(elem: Elem) = {
    val stream = IOUtils.toInputStream(xmlStringBuilder(elem))
    getDocumentBuilder.parse(stream).getDocumentElement
  }
}
