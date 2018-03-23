// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search

import com.yahoo.config.model.provision.InMemoryProvisioner
import com.yahoo.config.model.test.MockApplicationPackage
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg
import org.junit.Test

import org.junit.Assert.assertNotNull
import scala.xml.{XML, Elem}
import java.io.StringWriter
import com.yahoo.config.model.deploy.{DeployProperties, DeployState}

/**
 * @author tonytv
 */
class ImplicitIndexingClusterTest {
  @Test
  def existing_jdisc_is_used_as_indexing_cluster_when_multitenant() {
    val servicesXml =
      <services version="1.0">
        <jdisc version="1.0" id="jdisc">
          <search />
          <nodes count="1" />
          {accessControlXml}
        </jdisc>
        <content id="music" version="1.0">
          <redundancy>1</redundancy>
          <documents>
            <document type="music" mode="index" />
          </documents>
          <nodes count="1" />
        </content>
    </services>


    val vespaModel = buildMultiTenantVespaModel(servicesXml)
    val jdisc = vespaModel.getContainerClusters.get("jdisc")
    assertNotNull("Docproc not added to jdisc", jdisc.getDocproc)
    assertNotNull("Indexing chain not added to jdisc", jdisc.getDocprocChains.allChains().getComponent("indexing"))
  }

  private val accessControlXml =
    <http>
      <filtering>
        <access-control domain="foo" />
      </filtering>
      <server id="bar" port="4080" />
    </http>


  def buildMultiTenantVespaModel(servicesXml: Elem) = {
    val properties = new DeployProperties.Builder().multitenant(true).hostedVespa(true).build()
    val deployStateBuilder = new DeployState.Builder()
      .properties(properties)
      .modelHostProvisioner(new InMemoryProvisioner(true, "host1.yahoo.com", "host2.yahoo.com", "host3.yahoo.com"))

    val writer = new StringWriter
    XML.write(writer, servicesXml, "UTF-8", xmlDecl = true, doctype = null)
    writer.close()

    new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
      .withServices(writer.toString)
      .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
      .build())
      .create(deployStateBuilder)
  }
}
