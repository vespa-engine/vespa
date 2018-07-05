// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.vespa.config.search.core.PartitionsConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class TldTest {

    @Test
    public void requireThatServicesIsParsed() {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts("<hosts><host name='localhost'><alias>mockhost</alias></host><host name='my.other.host'><alias>mockhost2</alias></host></hosts>")
                .withServices(
                        "<services>" +
                                "  <admin version='2.0'>" +
                                "    <adminserver hostalias='mockhost' />" +
                                "  </admin>" +
                                "  <jdisc version='1.0' id='default'>" +
                                "    <search />" +
                                "    <nodes>" +
                                "      <node hostalias='mockhost'/>" +
                                "    </nodes>" +
                                "  </jdisc>" +
                                "  <content version='1.0' id='foo'>" +
                                "    <redundancy>1</redundancy>" +
                                "    <documents>" +
                                "      <document type='music' mode='index'/>" +
                                "    </documents>" +
                                "    <group>" +
                                "      <node hostalias='mockhost' distribution-key='0'/>" +
                                "      <node hostalias='mockhost2' distribution-key='1'/>" +
                                "    </group>" +
                                "    <tuning>" +
                                "      <dispatch>" +
                                "        <max-hits-per-partition>69</max-hits-per-partition>" +
                                "        <use-local-node>true</use-local-node>" +
                                "      </dispatch>" +
                                "    </tuning>" +
                                "  </content>" +
                                "</services>")
                .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
                .build();

        PartitionsConfig.Builder builder = new PartitionsConfig.Builder();
        new TestDriver(true).buildModel(app).getConfig(builder, "foo/search/cluster.foo/tlds/default.0.tld.0");
        PartitionsConfig config = new PartitionsConfig(builder);

        assertEquals(1, config.dataset().size());
        assertEquals(69, config.dataset(0).maxhitspernode());
        assertEquals(1, config.dataset(0).engine().size());
    }

  @Test
  public void requireThatUseLocalPolicyIsOk() {
    ApplicationPackage app = new MockApplicationPackage.Builder()
            .withHosts(
            "<hosts>" +
                    "<host name='search.node1'><alias>search1</alias></host>" +
                    "<host name='search.node2'><alias>search2</alias></host>" +
                    "<host name='jdisc.host.other'><alias>gateway</alias></host>" +
            "</hosts>")
            .withServices(
            "<services>" +
                    "  <admin version='2.0'>" +
                    "    <adminserver hostalias='gateway' />" +
                    "  </admin>" +
                    "  <jdisc version='1.0' id='default'>" +
                    "    <search />" +
                    "    <nodes>" +
                    "      <node hostalias='search1'/>" +
                    "      <node hostalias='search2'/>" +
                    "    </nodes>" +
                    "  </jdisc>" +
                    "  <jdisc version='1.0' id='gw'>" +
                    "    <document-api/>" +
                    "    <nodes>" +
                    "      <node hostalias='gateway'/>" +
                    "    </nodes>" +
                    "  </jdisc>" +
                    "  <content version='1.0' id='foo'>" +
                    "    <redundancy>2</redundancy>" +
                    "    <documents>" +
                    "      <document type='music' mode='index'/>" +
                    "    </documents>" +
                    "    <group name='topGroup'>" +
                    "    <distribution partitions='1|*'/>" +
                    "     <group name='group1' distribution-key='0'>" +
                    "       <node hostalias='search1' distribution-key='0'/>" +
                    "     </group>" +
                    "     <group name='group2' distribution-key='1'>" +
                    "       <node hostalias='search2' distribution-key='1'/>" +
                    "     </group>" +
                    "    </group>" +
                    "    <tuning>" +
                    "      <dispatch>" +
                    "        <use-local-node>true</use-local-node>" +
                    "      </dispatch>" +
                    "    </tuning>" +
                    "  </content>" +
                    "</services>")
            .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
            .build();

    PartitionsConfig.Builder builder = new PartitionsConfig.Builder();
    new TestDriver(true).buildModel(app).getConfig(builder, "foo/search/cluster.foo/tlds/gw.0.tld.0");
    PartitionsConfig config = new PartitionsConfig(builder);

    assertEquals(1, config.dataset().size());
    //gateway TLD with no local search node gets all search nodes
    assertEquals(2, config.dataset(0).engine().size());

    assertEquals("rowid not equal 0",0,config.dataset(0).engine(0).rowid()); //Load Balance row 0
    assertEquals("partid not equal 0",0,config.dataset(0).engine(0).partid());
    assertTrue("Not configured with correct search node",config.dataset(0).engine(0).name_and_port().contains("search.node1"));

    assertEquals("rowid not equal to 1",1,config.dataset(0).engine(1).rowid()); //Load Balance row 1
    assertEquals("partid no equal to 0",0,config.dataset(0).engine(1).partid());
    assertTrue("Not configured with correct search node",config.dataset(0).engine(1).name_and_port().contains("search.node2"));

    //First container with a local search node
    builder = new PartitionsConfig.Builder();
    new TestDriver(true).buildModel(app).getConfig(builder, "foo/search/cluster.foo/tlds/default.0.tld.0");
    config = new PartitionsConfig(builder);

    assertEquals(1, config.dataset().size());
    assertEquals(1, config.dataset(0).engine().size());
    assertEquals(0,config.dataset(0).engine(0).rowid());
    assertEquals(0,config.dataset(0).engine(0).partid());
    assertTrue("Not configured with local search node as engine",config.dataset(0).engine(0).name_and_port().contains("search.node1"));

    //Second container with a local search node
    builder = new PartitionsConfig.Builder();
    new TestDriver(true).buildModel(app).getConfig(builder, "foo/search/cluster.foo/tlds/default.1.tld.1");
    config = new PartitionsConfig(builder);

    assertEquals(1, config.dataset().size());
    assertEquals(1, config.dataset(0).engine().size());
    assertEquals(0,config.dataset(0).engine(0).rowid());
    assertEquals(0,config.dataset(0).engine(0).partid());
    assertTrue("Not configured with local search node as engine",config.dataset(0).engine(0).name_and_port().contains("search.node2"));

  }
}
