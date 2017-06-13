// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone


import com.yahoo.container.standalone.StandaloneContainerTest._
import com.yahoo.vespa.model.AbstractService
import org.junit.Assert._
import org.junit.Test

import scala.util.Try


/**
 * @author tonytv
 * @author gjoranv
 */

class StandaloneContainerTest {
  @Test
  def container_is_allowed_root_element() {
    StandaloneContainer.withContainerModel(plainXml) { root =>  }
  }

  @Test
  def services_is_allowed_root_element() {
    val servicesXml =
      <services>
        <container version="1.0" />
      </services>

    StandaloneContainer.withContainerModel(servicesXml) { root =>  }
  }

  @Test
  def multiple_container_elements_cannot_be_deployed() {
    val twoContainersXml =
      <services>
        <container id="container-1" version="1.0" />
        <container id="container-2" version="1.0" />
      </services>

    assertTrue(
      Try {
        StandaloneContainer.withContainerModel(twoContainersXml) { root =>  }
      }.isFailure)
  }

  @Test
  def application_preprocessor_is_run() {
    val servicesXml =
      <services xmlns:preprocess="properties">
        <preprocess:properties>
          <container_id>container-1</container_id>
        </preprocess:properties>
        <container id="${container_id}" version="1.0" />
      </services>
    StandaloneContainer.withContainerModel(servicesXml) {
      root =>
        assertTrue(root.getConfigProducer("container-1/standalone").isPresent)
    }
  }

  @Test
  def no_default_ports_are_enabled_when_using_http() {
    val xml =
      <jdisc version="1.0">
        <http>
          <server port="4000" id="server1" />
        </http>
      </jdisc>

    StandaloneContainer.withContainerModel(xml) { root =>
      val container = root.getConfigProducer("jdisc/standalone").get().asInstanceOf[AbstractService]
      println("portCnt: " + container.getPortCount)
      println("numPorts: " + container.getNumPortsAllocated)
      assertEquals(1, container.getNumPortsAllocated)
    }
  }

}

object StandaloneContainerTest {

  val plainXml = <container version="1.0" />
}
