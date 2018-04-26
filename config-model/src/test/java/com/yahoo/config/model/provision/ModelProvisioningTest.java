// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Slobrok;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.Dispatch;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.vespa.model.test.VespaModelTester;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Ignore;
import org.junit.Test;

import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsIn.isIn;
import static org.hamcrest.core.Every.everyItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for provisioning nodes to entire vespamodels
 *
 * @author Vegard Havdal
 * @author bratseth
 */
public class ModelProvisioningTest {

    @Test
    public void testNodeCountForJdisc() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>\n" +
                        "\n" +
                        "<admin version='3.0'><nodes count='1' /></admin>\n" +
                        "<jdisc id='mydisc' version='1.0'>" +
                        "  <handler id='myHandler'>" +
                        "    <component id='injected' />" +
                        "  </handler>" +
                        "  <nodes count=\"3\"/>" +
                        "</jdisc>" +
                        "<jdisc id='mydisc2' version='1.0'>" +
                        "  <document-processing/>" +
                        "  <handler id='myHandler'>" +
                        "    <component id='injected' />" +
                        "  </handler>" +
                        "  <nodes count='2' allocated-memory='45%' jvmargs='-verbosegc' preload='lib/blablamalloc.so'/>" +
                        "</jdisc>" +
                        "</services>";
        String hosts ="<hosts>"
                + " <host name='myhost0'>"
                + "  <alias>node0</alias>"
                + " </host>"
                + " <host name='myhost1'>"
                + "  <alias>node1</alias>"
                + " </host>"
                + " <host name='myhost2'>"
                + "  <alias>node2</alias>"
                + " </host>"
                + " <host name='myhost3'>"
                + "  <alias>node3</alias>"
                + " </host>"
                + " <host name='myhost4'>"
                + "  <alias>node4</alias>"
                + " </host>"
                + " <host name='myhost5'>"
                + "  <alias>node5</alias>"
                + " </host>"
                + "</hosts>";
        VespaModelCreatorWithMockPkg creator = new VespaModelCreatorWithMockPkg(null, services);
        VespaModel model = creator.create(new DeployState.Builder().modelHostProvisioner(new InMemoryProvisioner(Hosts.readFrom(new StringReader(hosts)), true)));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().size(), is(3));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(0).getConfigId(), is("mydisc/container.0"));
        assertTrue(model.getContainerClusters().get("mydisc").getContainers().get(0).isInitialized());
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(1).getConfigId(), is("mydisc/container.1"));
        assertTrue(model.getContainerClusters().get("mydisc").getContainers().get(1).isInitialized());
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(2).getConfigId(), is("mydisc/container.2"));
        assertTrue(model.getContainerClusters().get("mydisc").getContainers().get(2).isInitialized());

        assertThat(model.getContainerClusters().get("mydisc2").getContainers().size(), is(2));
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(0).getConfigId(), is("mydisc2/container.0"));
        assertTrue(model.getContainerClusters().get("mydisc2").getContainers().get(0).isInitialized());
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(1).getConfigId(), is("mydisc2/container.1"));
        assertTrue(model.getContainerClusters().get("mydisc2").getContainers().get(1).isInitialized());

        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(0).getJvmArgs(), is(""));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(1).getJvmArgs(), is(""));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(2).getJvmArgs(), is(""));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(0).getPreLoad(), is(getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so")));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(1).getPreLoad(), is(getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so")));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(2).getPreLoad(), is(getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so")));
        assertThat(model.getContainerClusters().get("mydisc").getMemoryPercentage(), is(Optional.empty()));

        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(0).getJvmArgs(), is("-verbosegc"));
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(1).getJvmArgs(), is("-verbosegc"));
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(0).getPreLoad(), is("lib/blablamalloc.so"));
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(1).getPreLoad(), is("lib/blablamalloc.so"));
        assertThat(model.getContainerClusters().get("mydisc2").getMemoryPercentage(), is(Optional.of(45)));
        
        HostSystem hostSystem = model.getHostSystem();
        assertNotNull(hostSystem.getHostByHostname("myhost0"));
        assertNotNull(hostSystem.getHostByHostname("myhost1"));
        assertNotNull(hostSystem.getHostByHostname("myhost2"));
        assertNotNull(hostSystem.getHostByHostname("myhost3"));
        assertNull(hostSystem.getHostByHostname("Nope"));
    }

    @Test
    public void testNodeCountForContentGroup() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "\n" +
                "  <admin version='3.0'>" +
                "    <nodes count='3'/>" +
                "  </admin>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        int numberOfHosts = 2;
        tester.addHosts(numberOfHosts);
        int numberOfContentNodes = 2;
        VespaModel model = tester.createModel(xmlWithNodes, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));
        final Map<String, ContentCluster> contentClusters = model.getContentClusters();
        ContentCluster cluster = contentClusters.get("bar");
        assertThat(cluster.getRootGroup().getNodes().size(), is(numberOfContentNodes));
        int i = 0;
        for (StorageNode node : cluster.getRootGroup().getNodes())
            assertEquals(i++, node.getDistributionKey());
    }

    @Test
    public void testSeparateClusters() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <search/>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <content version='1.0' id='content1'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(3);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
        assertEquals("Nodes in container1", 1, model.getContainerClusters().get("container1").getContainers().size());
        assertEquals("Heap size for container", 60, physicalMemoryPercentage(model.getContainerClusters().get("container1")));
    }

    @Test
    public void testClusterMembership() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(1);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals(1, model.getHostSystem().getHosts().size());
        HostResource host = model.getHostSystem().getHosts().iterator().next();

        assertEquals(1, host.clusterMemberships().size());
        ClusterMembership membership = host.clusterMemberships().iterator().next();
        assertEquals("container", membership.cluster().type().name());
        assertEquals("container1", membership.cluster().id().value());
    }

    @Test
    public void testCombinedCluster() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <search/>" +
                "     <nodes of='content1'/>" +
                "  </container>" +
                "  <content version='1.0' id='content1'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(2);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
        assertEquals("Nodes in container1", 2, model.getContainerClusters().get("container1").getContainers().size());
        assertEquals("Heap size is lowered with combined clusters", 
                     17, physicalMemoryPercentage(model.getContainerClusters().get("container1")));
    }

    @Test
    public void testCombinedClusterWithJvmArgs() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <document-processing/>" +
                "     <nodes of='content1' jvmargs='testarg'/>" +
                "  </container>" +
                "  <content version='1.0' id='content1'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(2);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
        assertEquals("Nodes in container1", 2, model.getContainerClusters().get("container1").getContainers().size());
        for (Container container : model.getContainerClusters().get("container1").getContainers())
            assertTrue(container.getJvmArgs().contains("testarg"));
    }

    @Test
    public void testMultipleCombinedClusters() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes of='content1'/>" +
                "  </container>" +
                "  <container version='1.0' id='container2'>" +
                "     <nodes of='content2'/>" +
                "  </container>" +
                "  <content version='1.0' id='content1'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "  <content version='1.0' id='content2'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='3'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(5);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
        assertEquals("Nodes in container1", 2, model.getContainerClusters().get("container1").getContainers().size());
        assertEquals("Nodes in content2", 3, model.getContentClusters().get("content2").getRootGroup().getNodes().size());
        assertEquals("Nodes in container2", 3, model.getContainerClusters().get("container2").getContainers().size());
    }

    @Test
    public void testNonExistingCombinedClusterReference() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes of='container2'/>" +
                "  </container>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(2);
        try {
            tester.createModel(xmlWithNodes, true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("container cluster 'container1' references service 'container2' but this service is not defined", e.getMessage());
        }
    }

    @Test
    public void testInvalidCombinedClusterReference() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes of='container2'/><!-- invalid; only content clusters can be referenced -->" +
                "  </container>" +
                "  <container version='1.0' id='container2'>" +
                "     <nodes count='2'/>" +
                "  </container>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(2);
        try {
            tester.createModel(xmlWithNodes, true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("container cluster 'container1' references service 'container2', but that is not a content service", e.getMessage());
        }
    }

    @Test
    public void testUsingNodesAndGroupCountAttributes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>2</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='27' groups='9'/>" +
                        "  </content>" +
                        "  <content version='1.0' id='baz'>" +
                        "     <redundancy>1</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='27' groups='27'/>" +
                        "   </content>" +
                        "</services>";

        int numberOfHosts = 64;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check container cluster
        assertEquals(1, model.getContainerClusters().size());
        Set<com.yahoo.vespa.model.Host> containerHosts = model.getContainerClusters().get("foo").getContainers().stream().map(Container::getHost).collect(Collectors.toSet());
        assertEquals(10, containerHosts.size());

        // Check admin clusters
        Admin admin = model.getAdmin();
        Set<com.yahoo.vespa.model.Host> slobrokHosts = admin.getSlobroks().stream().map(Slobrok::getHost).collect(Collectors.toSet());
        assertEquals(3, slobrokHosts.size());
        assertTrue("Slobroks are assigned from container nodes", containerHosts.containsAll(slobrokHosts));
        assertTrue("Logserver is assigned from container nodes", containerHosts.contains(admin.getLogserver().getHost()));
        assertEquals("No in-cluster config servers in a hosted environment", 0, admin.getConfigservers().size());
        assertEquals("No admin cluster controller when multitenant", null, admin.getClusterControllers());

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("default28", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("default54", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("default51", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(9, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertEquals("default54", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getConfigId(), is("bar/storage/1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getDistributionKey(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getConfigId(), is("bar/storage/2"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/3"));
        assertEquals("default51", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getDistributionKey(), is(4));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getConfigId(), is("bar/storage/4"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getDistributionKey(), is(5));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getConfigId(), is("bar/storage/5"));
        // ...
        assertEquals("default48", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
        // ...
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getIndex(), is("8"));
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getNodes().size(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getNodes().get(0).getDistributionKey(), is(24));
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getNodes().get(0).getConfigId(), is("bar/storage/24"));
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getNodes().get(1).getDistributionKey(), is(25));
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getNodes().get(1).getConfigId(), is("bar/storage/25"));
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getNodes().get(2).getDistributionKey(), is(26));
        assertThat(cluster.getRootGroup().getSubgroups().get(8).getNodes().get(2).getConfigId(), is("bar/storage/26"));

        cluster = model.getContentClusters().get("baz");
        clusterControllers = cluster.getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("baz-controllers", clusterControllers.getName());
        assertEquals("default01", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("default27", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("default26", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(27, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("baz/storage/0"));
        assertEquals("default27", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("baz/storage/1"));
        assertEquals("default26", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        // ...
        assertEquals("default25", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
        // ...
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getIndex(), is("26"));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().get(0).getDistributionKey(), is(26));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().get(0).getConfigId(), is("baz/storage/26"));
    }

    @Test
    public void testGroupsOfSize1() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>1</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='8' groups='8'/>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 18;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check content cluster
        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("default01", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("default08", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("default07", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(8, cluster.getRootGroup().getSubgroups().size());
        assertEquals(8, cluster.distributionBits());
        // first group
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertEquals("default08", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        // second group
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/1"));
        assertEquals("default07", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        // ... last group
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getIndex(), is("7"));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getDistributionKey(), is(7));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getConfigId(), is("bar/storage/7"));
        assertEquals("default01", cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getHostName());
    }

    @Test
    public void testExplicitNonDedicatedClusterControllers() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>2</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <controllers><nodes dedicated='false' count='6'/></controllers>" +
                        "     <nodes count='9' groups='3'/>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 19;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals( 8, cluster.distributionBits());
        assertEquals("We get the closest odd number", 5, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("default01", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("default02", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("default04", clusterControllers.getContainers().get(2).getHostName());
        assertEquals("default05", clusterControllers.getContainers().get(3).getHostName());
        assertEquals("default09", clusterControllers.getContainers().get(4).getHostName());
        assertEquals("default09", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertEquals("default08", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getHostName());
        assertEquals("default06", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        assertEquals("default03", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
    }

    @Test
    public void testClusterControllersWithGroupSize2() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <admin version='4.0'/>" +
                "  <container version='1.0' id='foo'>" +
                "     <nodes count='10'/>" +
                "  </container>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='8' groups='4'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 18;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals("We get the closest odd number", 3, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("default01", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("default08", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("default06", clusterControllers.getContainers().get(2).getHostName());
    }

    @Test
    public void testClusterControllersCanSupplementWithAllContainerClusters() throws ParseException {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <admin version='4.0'/>" +
                "  <container version='1.0' id='foo1'>" +
                "     <nodes count='2'/>" +
                "  </container>" +
                "  <container version='1.0' id='foo2'>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <controllers><nodes dedicated='false' count='5'/></controllers>" +
                "     <nodes count='2'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 5;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(1, clusterControllers.getContainers().size()); // TODO: Expected 5 with this feature reactivated
    }

    @Test
    public void testClusterControllersAreNotPlacedOnRetiredNodes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>2</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='9' groups='3'/>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 19;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, "default09", "default06", "default03");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("Skipping retired default09", "default01", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("Skipping retired default06", "default08", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("Skipping retired default03", "default05", clusterControllers.getContainers().get(2).getHostName());
    }

    @Test
    public void testSlobroksClustersAreExpandedToIncludeRetiredNodes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "</services>";

        int numberOfHosts = 10;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, "default09");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        assertEquals("Includes retired node", 1+3, model.getAdmin().getSlobroks().size());
        assertEquals("default01", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("default02", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("default10", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("Included in addition because it is retired", "default09", model.getAdmin().getSlobroks().get(3).getHostName());
    }

    @Test
    public void testSlobroksClustersAreExpandedToIncludeRetiredNodesWhenRetiredComesLast() throws ParseException {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "</services>";

        int numberOfHosts = 10;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, "default09", "default08");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        assertEquals("Includes retired node", 3+2, model.getAdmin().getSlobroks().size());
        assertEquals("default01", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("default02", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("default10", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("Included in addition because it is retired", "default08", model.getAdmin().getSlobroks().get(3).getHostName());
        assertEquals("Included in addition because it is retired", "default09", model.getAdmin().getSlobroks().get(4).getHostName());
    }

    @Test
    public void testSlobroksAreSpreadOverAllContainerClusters() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "  <container version='1.0' id='bar'>" +
                        "     <nodes count='3'/>" +
                        "  </container>" +
                        "</services>";

        int numberOfHosts = 13;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, "default12", "default03", "default02");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        // ... from cluster default
        assertEquals("Includes retired node", 3+3, model.getAdmin().getSlobroks().size());
        assertEquals("default04", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("default13", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("Included in addition because it is retired", "default12", model.getAdmin().getSlobroks().get(2).getHostName());
        // ... from cluster bar
        assertEquals("default01", model.getAdmin().getSlobroks().get(3).getHostName());
        assertEquals("Included in addition because it is retired", "default02", model.getAdmin().getSlobroks().get(4).getHostName());
        assertEquals("Included in addition because it is retired", "default03", model.getAdmin().getSlobroks().get(5).getHostName());
    }

    @Test
    public void testSlobroksAreSpreadOverAllContainerClustersExceptNodeAdmin() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <container version='1.0' id='routing'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "  <container version='1.0' id='node-admin'>" +
                        "     <nodes count='3'/>" +
                        "  </container>" +
                        "</services>";

        int numberOfHosts = 13;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        tester.setApplicationId("hosted-vespa", "routing", "default");
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        Set<String> routingHosts = getClusterHostnames(model, "routing");
        assertEquals(10, routingHosts.size());

        Set<String> nodeAdminHosts = getClusterHostnames(model, "node-admin");
        assertEquals(3, nodeAdminHosts.size());

        Set<String> slobrokHosts = model.getAdmin().getSlobroks().stream()
                .map(AbstractService::getHostName)
                .collect(Collectors.toSet());
        assertEquals(3, slobrokHosts.size());

        assertThat(slobrokHosts, everyItem(isIn(routingHosts)));
        assertThat(slobrokHosts, everyItem(not(isIn(nodeAdminHosts))));
    }

    private Set<String> getClusterHostnames(VespaModel model, String clusterId) {
        return model.getHosts().stream()
                .filter(host -> host.getServices().stream()
                        .anyMatch(serviceInfo -> Objects.equals(
                                serviceInfo.getProperty("clustername"),
                                Optional.of(clusterId))))
                .map(HostInfo::getHostname)
                .collect(Collectors.toSet());
    }

    @Test
    public void test2ContentNodesProduces1ClusterController() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>2</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='2'/>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 2;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(1, clusterControllers.getContainers().size());
    }

    @Test
    public void test2ContentNodesWithContainerClusterProducesMixedClusterControllerCluster() throws ParseException {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <container version='1.0' id='foo'>" +
                "     <nodes count='3'/>" +
                "  </container>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "  </content>" +
                "</services>";

        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(5);
        VespaModel model = tester.createModel(services, true);

        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(1, clusterControllers.getContainers().size()); // TODO: Expected 3 with this feature reactivated
    }

    @Ignore // TODO: unignore when feature is enabled again
    @Test
    public void test2ContentNodesOn2ClustersWithContainerClusterProducesMixedClusterControllerCluster() throws ParseException {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <container version='1.0' id='container'>" +
                "     <nodes count='3' flavor='container-node'/>" +
                "  </container>" +
                "  <content version='1.0' id='content1'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' flavor='content1-node'/>" +
                "  </content>" +
                "  <content version='1.0' id='content2'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' flavor='content2-node'/>" +
                "  </content>" +
                "</services>";

        VespaModelTester tester = new VespaModelTester();
        // use different flavors to make the test clearer
        tester.addHosts("container-node", 3);
        tester.addHosts("content1-node",  2);
        tester.addHosts("content2-node",  2);
        VespaModel model = tester.createModel(services, true);

        ContentCluster cluster1 = model.getContentClusters().get("content1");
        ContainerCluster clusterControllers1 = cluster1.getClusterControllers();
        assertEquals(1, clusterControllers1.getContainers().size());
        assertEquals("content1-node0",  clusterControllers1.getContainers().get(0).getHostName());
        assertEquals("content1-node1",  clusterControllers1.getContainers().get(1).getHostName());
        assertEquals("container-node0", clusterControllers1.getContainers().get(2).getHostName());

        ContentCluster cluster2 = model.getContentClusters().get("content2");
        ContainerCluster clusterControllers2 = cluster2.getClusterControllers();
        assertEquals(3, clusterControllers2.getContainers().size());
        assertEquals("content2-node0",  clusterControllers2.getContainers().get(0).getHostName());
        assertEquals("content2-node1",  clusterControllers2.getContainers().get(1).getHostName());
        assertEquals("We do not pick the container used to supplement another cluster",
                     "container-node1", clusterControllers2.getContainers().get(2).getHostName());
    }

    @Test
    public void testExplicitDedicatedClusterControllers() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='10'/>" +
                        "  </container>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>2</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <controllers><nodes dedicated='true' count='4'/></controllers>" +
                        "     <nodes count='9' groups='3'/>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 23;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(4, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("default04", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("default03", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("default02", clusterControllers.getContainers().get(2).getHostName());
        assertEquals("default01", clusterControllers.getContainers().get(3).getHostName());
    }

    @Test
    public void testUsingNodesAndGroupCountAttributesAndGettingTooFewNodes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "  <admin version='3.0'>" +
                        "    <nodes count='3'/>" + // Ignored
                        "  </admin>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy reply-after='3'>4</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='24' groups='3'/>" +
                        "     <engine><proton><searchable-copies>3</searchable-copies></proton></engine>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 6; // We only have 6 content nodes -> 3 groups with redundancy 2 in each
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(2*3, cluster.redundancy().effectiveInitialRedundancy()); // Reduced from 3*3
        assertEquals(2*3, cluster.redundancy().effectiveFinalRedundancy()); // Reduced from 3*4
        assertEquals(2*3, cluster.redundancy().effectiveReadyCopies()); // Reduced from 3*3
        assertEquals("2|2|*", cluster.getRootGroup().getPartitions().get()); // Reduced from 4|4|*
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(3, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getConfigId(), is("bar/storage/1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/2"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getDistributionKey(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getConfigId(), is("bar/storage/3"));
        assertThat(cluster.getRootGroup().getSubgroups().get(2).getIndex(), is("2"));
        assertThat(cluster.getRootGroup().getSubgroups().get(2).getNodes().size(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getDistributionKey(), is(4));
        assertThat(cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getConfigId(), is("bar/storage/4"));
        assertThat(cluster.getRootGroup().getSubgroups().get(2).getNodes().get(1).getDistributionKey(), is(5));
        assertThat(cluster.getRootGroup().getSubgroups().get(2).getNodes().get(1).getConfigId(), is("bar/storage/5"));
    }

    @Test
    public void testUsingNodesCountAttributesAndGettingTooFewNodes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "  <admin version='3.0'>" +
                        "    <nodes count='3'/>" + // Ignored
                        "  </admin>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy reply-after='8'>12</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='24'/>" +
                        "     <engine><proton><searchable-copies>5</searchable-copies></proton></engine>" +
                        "     <dispatch><num-dispatch-groups>7</num-dispatch-groups></dispatch>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 4;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(4, cluster.redundancy().effectiveInitialRedundancy());
        assertEquals(4, cluster.redundancy().effectiveFinalRedundancy());
        assertEquals(4, cluster.redundancy().effectiveReadyCopies());
        assertEquals(4, cluster.getSearch().getIndexed().getDispatchSpec().getGroups().size());
        assertFalse(cluster.getRootGroup().getPartitions().isPresent());
        assertEquals(4, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getNodes().size(), is(4));
        assertThat(cluster.getRootGroup().getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertThat(cluster.getRootGroup().getNodes().get(1).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getNodes().get(1).getConfigId(), is("bar/storage/1"));
        assertThat(cluster.getRootGroup().getNodes().get(2).getDistributionKey(), is(2));
        assertThat(cluster.getRootGroup().getNodes().get(2).getConfigId(), is("bar/storage/2"));
        assertThat(cluster.getRootGroup().getNodes().get(3).getDistributionKey(), is(3));
        assertThat(cluster.getRootGroup().getNodes().get(3).getConfigId(), is("bar/storage/3"));
    }

    @Test
    public void testUsingNodesAndGroupCountAttributesAndGettingJustOneNode() throws ParseException {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='3.0'>" +
                        "    <nodes count='3'/>" + // Ignored
                        "  </admin>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy reply-after='3'>4</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='24' groups='3'/>" +
                        "     <engine><proton><searchable-copies>3</searchable-copies></proton></engine>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 1; // We only have 1 content node -> 1 groups with redundancy 1
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(1, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("default01", clusterControllers.getContainers().get(0).getHostName());
        assertEquals(1, cluster.redundancy().effectiveInitialRedundancy()); // Reduced from 3*3
        assertEquals(1, cluster.redundancy().effectiveFinalRedundancy()); // Reduced from 3*4
        assertEquals(1, cluster.redundancy().effectiveReadyCopies()); // Reduced from 3*3
        assertFalse(cluster.getRootGroup().getPartitions().isPresent()); // 1 group - > flattened -> no distribution
        assertEquals(1, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getNodes().get(0).getConfigId(), is("bar/storage/0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRequiringMoreNodesThanAreAvailable() throws ParseException {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='3' required='true'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 2;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        tester.createModel(services, false);
    }

    @Test
    public void testUsingNodesCountAttributesAndGettingJustOneNode() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='3.0'>" +
                        "    <nodes count='3'/>" + // Ignored
                        "  </admin>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy reply-after='8'>12</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='24'/>" +
                        "     <engine><proton><searchable-copies>5</searchable-copies></proton></engine>" +
                        "     <dispatch><num-dispatch-groups>7</num-dispatch-groups></dispatch>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 1;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(1, cluster.redundancy().effectiveInitialRedundancy());
        assertEquals(1, cluster.redundancy().effectiveFinalRedundancy());
        assertEquals(1, cluster.redundancy().effectiveReadyCopies());

        assertEquals(1, cluster.getSearch().getIndexed().getDispatchSpec().getGroups().size());
        assertFalse(cluster.getRootGroup().getPartitions().isPresent());
        assertEquals(1, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getNodes().get(0).getConfigId(), is("bar/storage/0"));
    }

    @Test
    public void testRequestingSpecificFlavors() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'>" +
                        "     <logservers><nodes count='1' dedicated='true' flavor='logserver-flavor'/></logservers>" +
                        "     <slobroks><nodes count='2' dedicated='true' flavor='slobrok-flavor'/></slobroks>" +
                        "  </admin>" +
                        "  <container version='1.0' id='container'>" +
                        "     <nodes count='4' flavor='container-flavor'/>" +
                        "  </container>" +
                        "  <content version='1.0' id='foo'>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <controllers><nodes count='2' dedicated='true' flavor='controller-foo-flavor'/></controllers>" +
                        "     <nodes count='5' flavor='content-foo-flavor'/>" +
                        "  </content>" +
                        "  <content version='1.0' id='bar'>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <controllers><nodes count='3' dedicated='true' flavor='controller-bar-flavor'/></controllers>" +
                        "     <nodes count='6' flavor='content-bar-flavor'/>" +
                        "  </content>" +
                        "</services>";

        int totalHosts = 23;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts("logserver-flavor", 1);
        tester.addHosts("slobrok-flavor", 2);
        tester.addHosts("container-flavor", 4);
        tester.addHosts("controller-foo-flavor", 2);
        tester.addHosts("content-foo-flavor", 5);
        tester.addHosts("controller-bar-flavor", 3);
        tester.addHosts("content-bar-flavor", 6);
        VespaModel model = tester.createModel(services, true, 0); // fails unless the right flavors+counts are requested
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(totalHosts));
    }

    @Test
    public void testJDiscOnly() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<jdisc version='1.0'>" +
                        "  <search/>" +
                        "  <nodes count='3'/>" +
                        "</jdisc>";
        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertEquals(numberOfHosts, model.getRoot().getHostSystem().getHosts().size());
        assertEquals(3, model.getContainerClusters().get("jdisc").getContainers().size());
        assertNotNull(model.getAdmin().getLogserver());
        assertEquals(3, model.getAdmin().getSlobroks().size());
    }

    @Test
    public void testUsingHostaliasWithProvisioner() {
        String services =
                        "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "<admin version='2.0'>" +
                        "  <adminserver hostalias='node1'/>\n"+
                        "</admin>\n" +
                        "<jdisc id='mydisc' version='1.0'>" +
                        "  <handler id='myHandler'>" +
                        "    <component id='injected' />" +
                        "  </handler>" +
                        "  <nodes>" +
                        "    <node hostalias='node1'/>" +
                        "  </nodes>" +
                        "</jdisc>" +
                        "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertEquals(1, model.getRoot().getHostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
    }

    @Test
    public void testThatStandaloneSyntaxWorksOnHostedVespa() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<jdisc id='foo' version='1.0'>" +
                "  <http>" +
                "    <server id='server1' port='" + getDefaults().vespaWebServicePort() + "' />" +
                "  </http>" +
                "</jdisc>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getHosts().size(), is(1));
        assertThat(model.getContainerClusters().size(), is(1));
    }

    @Test
    public void testNoNodeTagMeans1Node() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <jdisc id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "  </jdisc>" +
                "  <content version='1.0' id='bar'>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "  </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertEquals(1, model.getRoot().getHostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
        assertEquals(1, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().countNodes());
    }

    @Test
    public void testNoNodeTagMeans1NodeNoContent() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <jdisc id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "  </jdisc>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertEquals(1, model.getRoot().getHostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
        assertEquals(1, model.getContainerClusters().get("foo").getContainers().size());
    }

    @Test
    public void testNoNodeTagMeans1NodeNonHosted() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <jdisc id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "  </jdisc>" +
                "  <content version='1.0' id='bar'>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "  </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(false);
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertEquals(1, model.getRoot().getHostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
        assertEquals(1, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().recursiveGetNodes().size());
    }

    @Test
    public void testSingleNodeNonHosted() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <jdisc id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "    <nodes><node hostalias='foo'/></nodes>"+
                "  </jdisc>" +
                "  <content version='1.0' id='bar'>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "    <nodes><node hostalias='foo' distribution-key='0'/></nodes>"+
                "  </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(false);
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertEquals(1, model.getRoot().getHostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
        assertEquals(1, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().countNodes());
    }

    /** Recreate the combination used in some factory tests */
    @Test
    public void testMultitenantButNotHosted() {
        String services =
        "<?xml version='1.0' encoding='UTF-8' ?>" +
        "<services version='1.0'>" +
        "  <admin version='2.0'>" +
        "    <adminserver hostalias='node1'/>" +
        "  </admin>"  +
        "   <jdisc id='default' version='1.0'>" +
        "     <search/>" +
        "     <nodes>" +
        "       <node hostalias='node1'/>" +
        "     </nodes>" +
        "   </jdisc>" +
        "   <content id='storage' version='1.0'>" +
        "     <redundancy>2</redundancy>" +
        "     <group>" +
        "       <node distribution-key='0' hostalias='node1'/>" +
        "       <node distribution-key='1' hostalias='node1'/>" +
        "     </group>" +
        "     <tuning>" +
        "       <cluster-controller>" +
        "         <transition-time>0</transition-time>" +
        "       </cluster-controller>" +
        "     </tuning>" +
        "     <documents>" +
        "       <document mode='store-only' type='type1'/>" +
        "     </documents>" +
        "     <engine>" +
        "       <proton/>" +
        "     </engine>" +
        "   </content>" +
        " </services>";

        VespaModel model = createNonProvisionedMultitenantModel(services);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(1));
        ContentCluster content = model.getContentClusters().get("storage");
        assertEquals(2, content.getRootGroup().getNodes().size());
        ContainerCluster controller = content.getClusterControllers();
        assertEquals(1, controller.getContainers().size());
    }

    @Test
    public void testModelWithReferencedIndexingCluster() {
        String services =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services version=\"1.0\">\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"vespa-1\"/>\n" +
                "    <configservers>\n" +
                "      <configserver hostalias=\"vespa-1\"/>\n" +
                "    </configservers>\n" +
                "  </admin>\n" +
                "\n" +
                "  <container id=\"container\" version=\"1.0\">\n" +
                "    <document-processing/>\n" +
                "    <document-api/>\n" +
                "    <search/>\n" +
                "    <nodes jvmargs=\"-Xms512m -Xmx512m\">\n" +
                "      <node hostalias=\"vespa-1\"/>\n" +
                "    </nodes>\n" +
                "  </container>\n" +
                "\n" +
                "  <content id=\"storage\" version=\"1.0\">\n" +
                "    <search>\n" +
                "      <visibility-delay>1.0</visibility-delay>\n" +
                "    </search>\n" +
                "    <redundancy>2</redundancy>\n" +
                "    <documents>\n" +
                "      <document type=\"type1\" mode=\"index\"/>\n" +
                "      <document-processing cluster=\"container\"/>\n" +
                "    </documents>\n" +
                "    <nodes>\n" +
                "      <node hostalias=\"vespa-1\" distribution-key=\"0\"/>\n" +
                "    </nodes>\n" +
                "  </content>\n" +
                "\n" +
                "</services>";

        VespaModel model = createNonProvisionedMultitenantModel(services);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(1));
        ContentCluster content = model.getContentClusters().get("storage");
        assertEquals(1, content.getRootGroup().getNodes().size());
        ContainerCluster controller = content.getClusterControllers();
        assertEquals(1, controller.getContainers().size());
    }

    @Test
    public void testSharedNodesNotHosted() {
        String hosts =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<hosts>\n" +
                "  <host name=\"vespa-1\">\n" +
                "    <alias>vespa-1</alias>\n" +
                "  </host>\n" +
                "  <host name=\"vespa-2\">\n" +
                "    <alias>vespa-2</alias>\n" +
                "  </host>\n" +
                "  <host name=\"vespa-3\">\n" +
                "    <alias>vespa-3</alias>\n" +
                "  </host>\n" +
                "</hosts>";
        String services =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services version=\"1.0\">\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"vespa-1\"/>\n" +
                "    <configservers>\n" +
                "      <configserver hostalias=\"vespa-1\"/>\n" +
                "    </configservers>\n" +
                "  </admin>\n" +
                "\n" +
                "  <container id=\"container\" version=\"1.0\">\n" +
                "    <document-processing/>\n" +
                "    <document-api/>\n" +
                "    <search/>\n" +
                "    <nodes jvmargs=\"-Xms512m -Xmx512m\">\n" +
                "      <node hostalias=\"vespa-1\"/>\n" +
                "      <node hostalias=\"vespa-2\"/>\n" +
                "      <node hostalias=\"vespa-3\"/>\n" +
                "    </nodes>\n" +
                "  </container>\n" +
                "\n" +
                "  <content id=\"storage\" version=\"1.0\">\n" +
                "    <search>\n" +
                "      <visibility-delay>1.0</visibility-delay>\n" +
                "    </search>\n" +
                "    <redundancy>2</redundancy>\n" +
                "    <documents>\n" +
                "      <document type=\"type1\" mode=\"index\"/>\n" +
                "      <document-processing cluster=\"container\"/>\n" +
                "    </documents>\n" +
                "    <nodes>\n" +
                "      <node hostalias=\"vespa-1\" distribution-key=\"0\"/>\n" +
                "      <node hostalias=\"vespa-2\" distribution-key=\"1\"/>\n" +
                "      <node hostalias=\"vespa-3\" distribution-key=\"2\"/>\n" +
                "    </nodes>\n" +
                "  </content>\n" +
                "\n" +
                "</services>";

        VespaModel model = createNonProvisionedModel(false, hosts, services);
        assertEquals(3, model.getRoot().getHostSystem().getHosts().size());
        ContentCluster content = model.getContentClusters().get("storage");
        assertEquals(3, content.getRootGroup().getNodes().size());
    }

    @Test
    public void testMultitenantButNotHostedSharedContentNode() {
        String services =
                "<?xml version='1.0' encoding='UTF-8' ?>" +
                "<services version='1.0'>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node1'/>" +
                "  </admin>"  +
                "   <jdisc id='default' version='1.0'>" +
                "     <search/>" +
                "     <nodes>" +
                "       <node hostalias='node1'/>" +
                "     </nodes>" +
                "   </jdisc>" +
                "   <content id='storage' version='1.0'>" +
                "     <redundancy>2</redundancy>" +
                "     <group>" +
                "       <node distribution-key='0' hostalias='node1'/>" +
                "       <node distribution-key='1' hostalias='node1'/>" +
                "     </group>" +
                "     <tuning>" +
                "       <cluster-controller>" +
                "         <transition-time>0</transition-time>" +
                "       </cluster-controller>" +
                "     </tuning>" +
                "     <documents>" +
                "       <document mode='store-only' type='type1'/>" +
                "     </documents>" +
                "     <engine>" +
                "       <proton/>" +
                "     </engine>" +
                "   </content>" +
                "   <content id='search' version='1.0'>" +
                "     <redundancy>2</redundancy>" +
                "     <group>" +
                "       <node distribution-key='0' hostalias='node1'/>" +
                "     </group>" +
                "     <documents>" +
                "       <document type='type1'/>" +
                "     </documents>" +
                "   </content>" +
                " </services>";

        VespaModel model = createNonProvisionedMultitenantModel(services);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(1));
        ContentCluster content = model.getContentClusters().get("storage");
        assertEquals(2, content.getRootGroup().getNodes().size());
        ContainerCluster controller = content.getClusterControllers();
        assertEquals(1, controller.getContainers().size());
    }

    private VespaModel createNonProvisionedMultitenantModel(String services) {
        return createNonProvisionedModel(true, null, services);
    }

    private VespaModel createNonProvisionedModel(boolean multitenant, String hosts, String services) {
        VespaModelCreatorWithMockPkg modelCreatorWithMockPkg = new VespaModelCreatorWithMockPkg(hosts, services, ApplicationPackageUtils.generateSearchDefinition("type1"));
        ApplicationPackage appPkg = modelCreatorWithMockPkg.appPkg;
        DeployState deployState = new DeployState.Builder().applicationPackage(appPkg).
                properties((new DeployProperties.Builder()).multitenant(multitenant).build()).
                build(true);
        return modelCreatorWithMockPkg.create(false, deployState);
    }

    @Test
    public void testThatTldConfigIdsAreDeterministic() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'/>" +
                        "  <jdisc version='1.0' id='jdisc0'>" +
                        "     <search/>" +
                        "     <nodes count='2'/>" +
                        "  </jdisc>" +
                        "  <jdisc version='1.0' id='jdisc1'>" +
                        "     <search/>" +
                        "     <nodes count='2'/>" +
                        "  </jdisc>" +
                        "  <content version='1.0' id='content0'>" +
                        "     <redundancy>2</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='2'/>" +
                        "  </content>" +
                        "  <content version='1.0' id='content1'>" +
                        "     <redundancy>2</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "     <nodes count='2'/>" +
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 8;

        {
            VespaModelTester tester = new VespaModelTester();
            tester.addHosts(numberOfHosts);
            // Nodes used will be default0, default1, .. and so on.
            VespaModel model = tester.createModel(services, true);
            assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

            Map<String, ContentCluster> contentClusters = model.getContentClusters();
            assertEquals(2, contentClusters.size());

            checkThatTldAndContainerRunningOnSameHostHaveSameId(
                    model.getContainerClusters().values(),
                    model.getContentClusters().values(),
                    0);
        }

        {
            VespaModelTester tester = new VespaModelTester();
            tester.addHosts(numberOfHosts + 1);
            // Start numbering nodes with index 1 and retire first node
            // Nodes used will be default1, default2, .. and so on. Containers will start with index 1, not 0 as they are in the test above
            VespaModel model = tester.createModel(services, true, 1, "default0");
            assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

            Map<String, ContentCluster> contentClusters = model.getContentClusters();
            assertEquals(2, contentClusters.size());

            checkThatTldAndContainerRunningOnSameHostHaveSameId(
                    model.getContainerClusters().values(),
                    model.getContentClusters().values(),
                    1);
        }
    }

    private void checkThatTldAndContainerRunningOnSameHostHaveSameId(Collection<ContainerCluster> containerClusters,
                                                                     Collection<ContentCluster> contentClusters,
                                                                     int startIndexForContainerIds) {
        for (ContentCluster contentCluster : contentClusters) {
            String contentClusterName = contentCluster.getName();
            int i = 0;
            for (ContainerCluster containerCluster : containerClusters) {
                String containerClusterName = containerCluster.getName();
                for (int j = 0; j < 2; j++) {
                    Dispatch tld = contentCluster.getSearch().getIndexed().getTLDs().get(2 * i + j);
                    Container container = containerCluster.getContainers().get(j);
                    int containerConfigIdIndex = j + startIndexForContainerIds;

                    assertEquals(container.getHostName(), tld.getHostname());
                    assertEquals(contentClusterName + "/search/cluster." + contentClusterName + "/tlds/" +
                                    containerClusterName + "." + containerConfigIdIndex + ".tld." + containerConfigIdIndex,
                            tld.getConfigId());
                    assertEquals(containerClusterName + "/" + "container." + containerConfigIdIndex,
                            container.getConfigId());
                }
                i++;
            }
        }
    }

    private int physicalMemoryPercentage(ContainerCluster cluster) {
        QrStartConfig.Builder b = new QrStartConfig.Builder();
        cluster.getSearch().getConfig(b);
        return new QrStartConfig(b).jvm().heapSizeAsPercentageOfPhysicalMemory();
    }

    @Test
    public void require_that_proton_config_is_tuned_based_on_node_flavor() {
         String services = joinLines("<?xml version='1.0' encoding='utf-8' ?>",
                 "<services>",
                 "  <content version='1.0' id='test'>",
                 "     <documents>",
                 "       <document type='type1' mode='index'/>",
                 "     </documents>",
                 "     <nodes count='2' flavor='content-test-flavor'/>",
                 "  </content>",
                 "</services>");

         VespaModelTester tester = new VespaModelTester();
         tester.addHosts(createFlavorFromDiskSetting("content-test-flavor", false), 2);
         VespaModel model = tester.createModel(services, true, 0);
         ContentSearchCluster cluster = model.getContentClusters().get("test").getSearch();
         assertEquals(2, cluster.getSearchNodes().size());
         assertEquals(40, getProtonConfig(cluster, 0).hwinfo().disk().writespeed(), 0.001);
         assertEquals(40, getProtonConfig(cluster, 1).hwinfo().disk().writespeed(), 0.001);
    }

    private static Flavor createFlavorFromDiskSetting(String name, boolean fastDisk) {
        return new Flavor(new FlavorsConfig.Flavor(new FlavorsConfig.Flavor.Builder().
                name(name).fastDisk(fastDisk)));
    }

    private static ProtonConfig getProtonConfig(ContentSearchCluster cluster, int searchNodeIdx) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        List<SearchNode> searchNodes = cluster.getSearchNodes();
        assertTrue(searchNodeIdx < searchNodes.size());
        searchNodes.get(searchNodeIdx).getConfig(builder);
        return new ProtonConfig(builder);
    }

    @Test
    public void require_that_config_override_and_explicit_proton_tuning_have_precedence_over_default_node_flavor_tuning() {
        String services = joinLines("<?xml version='1.0' encoding='utf-8' ?>",
                "<services>",
                "  <content version='1.0' id='test'>",
                "    <config name='vespa.config.search.core.proton'>",
                "      <flush><memory><maxtlssize>2000</maxtlssize></memory></flush>",
                "    </config>",
                "    <documents>",
                "      <document type='type1' mode='index'/>",
                "    </documents>",
                "    <nodes count='1' flavor='content-test-flavor'/>",
                "    <engine>",
                "      <proton>",
                "        <tuning>",
                "          <searchnode>",
                "            <flushstrategy>",
                "              <native>",
                "                <total>",
                "                  <maxmemorygain>1000</maxmemorygain>",
                "                </total>",
                "              </native>",
                "            </flushstrategy>",
                "          </searchnode>",
                "        </tuning>",
                "      </proton>",
                "    </engine>",
                "  </content>",
                "</services>");

        VespaModelTester tester = new VespaModelTester();
        tester.addHosts("default", 1);
        tester.addHosts(createFlavorFromMemoryAndDisk("content-test-flavor", 128, 100), 1);
        VespaModel model = tester.createModel(services, true, 0);
        ContentSearchCluster cluster = model.getContentClusters().get("test").getSearch();
        ProtonConfig cfg = getProtonConfig(model, cluster.getSearchNodes().get(0).getConfigId());
        assertEquals(2000, cfg.flush().memory().maxtlssize()); // from config override
        assertEquals(1000, cfg.flush().memory().maxmemory()); // from explicit tuning
        assertEquals((long) 16 * GB, cfg.flush().memory().each().maxmemory()); // from default node flavor tuning
    }

    private static long GB = 1024 * 1024 * 1024;

    private static Flavor createFlavorFromMemoryAndDisk(String name, int memoryGb, int diskGb) {
        return new Flavor(new FlavorsConfig.Flavor(new FlavorsConfig.Flavor.Builder().
                name(name).minMainMemoryAvailableGb(memoryGb).minDiskAvailableGb(diskGb)));
    }

    private static ProtonConfig getProtonConfig(VespaModel model, String configId) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        model.getConfig(builder, configId);
        return new ProtonConfig(builder);
    }

}
