// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.config.content.core.StorStatusConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Logserver;
import com.yahoo.vespa.model.admin.Slobrok;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.vespa.model.test.VespaModelTester;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import com.yahoo.yolean.Exceptions;
import org.junit.Ignore;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.GB;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.reservedMemoryGb;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for provisioning nodes to entire Vespa models.
 *
 * @author Vegard Havdal
 * @author bratseth
 */
public class ModelProvisioningTest {

    @Test
    public void testNodesJdisc() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>\n" +
                        "\n" +
                        "<admin version='3.0'><nodes count='1' /></admin>\n" +
                        "<container id='mydisc' version='1.0'>" +
                        "  <handler id='myHandler'>" +
                        "    <component id='injected' />" +
                        "  </handler>" +
                        "  <nodes count=\"3\"/>" +
                        "</container>" +
                        "<container id='mydisc2' version='1.0'>" +
                        "  <document-processing/>" +
                        "  <handler id='myHandler'>" +
                        "    <component id='injected' />" +
                        "  </handler>" +
                        "  <nodes count='2' allocated-memory='45%' jvm-gc-options='-XX:+UseParNewGC' jvm-options='-verbosegc' preload='lib/blablamalloc.so'/>" +
                        "</container>" +
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
        VespaModel model = creator.create(new DeployState.Builder().modelHostProvisioner(new InMemoryProvisioner(Hosts.readFrom(new StringReader(hosts)), true, false)));
        ApplicationContainerCluster mydisc = model.getContainerClusters().get("mydisc");
        ApplicationContainerCluster mydisc2 = model.getContainerClusters().get("mydisc2");
        assertEquals(3, mydisc.getContainers().size());
        assertEquals("mydisc/container.0", (mydisc.getContainers().get(0).getConfigId()));
        assertTrue(mydisc.getContainers().get(0).isInitialized());
        assertEquals("mydisc/container.1", mydisc.getContainers().get(1).getConfigId());
        assertTrue(mydisc.getContainers().get(1).isInitialized());
        assertEquals("mydisc/container.2", mydisc.getContainers().get(2).getConfigId());
        assertTrue(mydisc.getContainers().get(2).isInitialized());

        assertEquals(2, mydisc2.getContainers().size());
        assertEquals("mydisc2/container.0", mydisc2.getContainers().get(0).getConfigId());
        assertTrue(mydisc2.getContainers().get(0).isInitialized());
        assertEquals("mydisc2/container.1", mydisc2.getContainers().get(1).getConfigId());
        assertTrue(mydisc2.getContainers().get(1).isInitialized());

        assertEquals("", mydisc.getContainers().get(0).getJvmOptions());
        assertEquals("", mydisc.getContainers().get(1).getJvmOptions());
        assertEquals("", mydisc.getContainers().get(2).getJvmOptions());
        assertEquals(getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so"), mydisc.getContainers().get(0).getPreLoad());
        assertEquals(getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so"), mydisc.getContainers().get(1).getPreLoad());
        assertEquals(getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so"), mydisc.getContainers().get(2).getPreLoad());
        assertEquals(Optional.empty(), mydisc.getMemoryPercentage());

        assertEquals("-verbosegc", mydisc2.getContainers().get(0).getJvmOptions());
        assertEquals("-verbosegc", mydisc2.getContainers().get(1).getJvmOptions());
        assertEquals("lib/blablamalloc.so", mydisc2.getContainers().get(0).getPreLoad());
        assertEquals("lib/blablamalloc.so", mydisc2.getContainers().get(1).getPreLoad());
        assertEquals(Optional.of(45), mydisc2.getMemoryPercentage());
        assertEquals(Optional.of("-XX:+UseParNewGC"), mydisc2.getJvmGCOptions());
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        mydisc2.getConfig(qrStartBuilder);
        QrStartConfig qrsStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals(45, qrsStartConfig.jvm().heapSizeAsPercentageOfPhysicalMemory());
        
        HostSystem hostSystem = model.hostSystem();
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
        int numberOfHosts = 5;
        tester.addHosts(numberOfHosts);
        int numberOfContentNodes = 2;
        VespaModel model = tester.createModel(xmlWithNodes, true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());
        Map<String, ContentCluster> contentClusters = model.getContentClusters();
        ContentCluster cluster = contentClusters.get("bar");
        assertEquals(numberOfContentNodes, cluster.getRootGroup().getNodes().size());
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
                "  <content version='1.0'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(8);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
        assertEquals("Nodes in container1", 1, model.getContainerClusters().get("container1").getContainers().size());
        assertEquals("Nodes in cluster without ID", 2, model.getContentClusters().get("content").getRootGroup().getNodes().size());
        assertEquals("Heap size for container", 60, physicalMemoryPercentage(model.getContainerClusters().get("container1")));
        assertProvisioned(2, ClusterSpec.Id.from("content1"), ClusterSpec.Type.content, model);
        assertProvisioned(1, ClusterSpec.Id.from("container1"), ClusterSpec.Type.container, model);
        assertProvisioned(2, ClusterSpec.Id.from("content"), ClusterSpec.Type.content, model);
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

        assertEquals(1, model.hostSystem().getHosts().size());
        HostResource host = model.hostSystem().getHosts().iterator().next();

        assertTrue(host.spec().membership().isPresent());
        assertEquals("container", host.spec().membership().get().cluster().type().name());
        assertEquals("container1", host.spec().membership().get().cluster().id().value());
    }

    @Test
    public void testCombinedCluster() {
        var containerElements = Set.of("jdisc", "container");
        for (var containerElement : containerElements) {
            String xmlWithNodes =
                    "<?xml version='1.0' encoding='utf-8' ?>" +
                    "<services>" +
                    "  <" + containerElement + " version='1.0' id='container1'>" +
                    "     <search/>" +
                    "     <nodes of='content1'/>" +
                    "  </" + containerElement + ">" +
                    "  <content version='1.0' id='content1'>" +
                    "     <redundancy>2</redundancy>" +
                    "     <documents>" +
                    "       <document type='type1' mode='index'/>" +
                    "     </documents>" +
                    "     <nodes count='2'>" +
                    "       <resources vcpu='1' memory='3Gb' disk='9Gb'/>" +
                    "     </nodes>" +
                    "   </content>" +
                    "</services>";
            VespaModelTester tester = new VespaModelTester();
            tester.dedicatedClusterControllerCluster(false);
            tester.addHosts(2);
            VespaModel model = tester.createModel(xmlWithNodes, true);
            assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
            assertEquals("Nodes in container1", 2, model.getContainerClusters().get("container1").getContainers().size());
            assertEquals("Heap size is lowered with combined clusters",
                         17, physicalMemoryPercentage(model.getContainerClusters().get("container1")));
            assertEquals("Memory for proton is lowered to account for the jvm heap",
                         (long)((3 - reservedMemoryGb) * (Math.pow(1024, 3)) * (1 - 0.17)), protonMemorySize(model.getContentClusters().get("content1")));
            assertProvisioned(0, ClusterSpec.Id.from("container1"), ClusterSpec.Type.container, model);
            assertProvisioned(2, ClusterSpec.Id.from("content1"), ClusterSpec.Id.from("container1"), ClusterSpec.Type.combined, model);
        }
    }

    /** For comparison with the above */
    @Test
    public void testNonCombinedCluster() {
        var containerElements = Set.of("jdisc", "container");
        for (var containerElement : containerElements) {
            String xmlWithNodes =
                    "<?xml version='1.0' encoding='utf-8' ?>" +
                    "<services>" +
                    "  <" + containerElement + " version='1.0' id='container1'>" +
                    "     <search/>" +
                    "     <nodes count='2'/>" +
                    "  </" + containerElement + ">" +
                    "  <content version='1.0' id='content1'>" +
                    "     <redundancy>2</redundancy>" +
                    "     <documents>" +
                    "       <document type='type1' mode='index'/>" +
                    "     </documents>" +
                    "     <nodes count='2'>" +
                    "       <resources vcpu='1' memory='3Gb' disk='9Gb'/>" +
                    "     </nodes>" +
                    "   </content>" +
                    "</services>";
            VespaModelTester tester = new VespaModelTester();
            tester.addHosts(7);
            VespaModel model = tester.createModel(xmlWithNodes, true);
            assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
            assertEquals("Nodes in container1", 2, model.getContainerClusters().get("container1").getContainers().size());
            assertEquals("Heap size is normal",
                         60, physicalMemoryPercentage(model.getContainerClusters().get("container1")));
            assertEquals("Memory for proton is normal",
                         (long)((3 - reservedMemoryGb) * (Math.pow(1024, 3))), protonMemorySize(model.getContentClusters().get("content1")));
        }
    }

    @Test
    public void testCombinedClusterWithJvmOptions() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <document-processing/>" +
                "     <nodes of='content1' jvm-options='testoption'/>" +
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
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(2);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals("Nodes in content1", 2, model.getContentClusters().get("content1").getRootGroup().getNodes().size());
        assertEquals("Nodes in container1", 2, model.getContainerClusters().get("container1").getContainers().size());
        for (Container container : model.getContainerClusters().get("container1").getContainers())
            assertTrue(container.getJvmOptions().contains("testoption"));
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
        tester.addHosts(8);
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
            assertEquals("container cluster 'container1' contains an invalid reference: referenced service 'container2' is not defined", Exceptions.toMessageString(e));
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
            assertEquals("container cluster 'container1' contains an invalid reference: service 'container2' is not a content service", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testCombinedClusterWithZooKeeperFails() {
        var containerElements = Set.of("jdisc", "container");
        for (var containerElement : containerElements) {
            String xmlWithNodes =
                    "<?xml version='1.0' encoding='utf-8' ?>" +
                    "<services>" +
                    "  <" + containerElement + " version='1.0' id='container1'>" +
                    "     <search/>" +
                    "     <nodes of='content1'/>" +
                    "     <zookeeper />" +
                    "  </" + containerElement + ">" +
                    "  <content version='1.0' id='content1'>" +
                    "     <redundancy>2</redundancy>" +
                    "     <documents>" +
                    "       <document type='type1' mode='index'/>" +
                    "     </documents>" +
                    "     <nodes count='2'>" +
                    "       <resources vcpu='1' memory='3Gb' disk='9Gb'/>" +
                    "     </nodes>" +
                    "   </content>" +
                    "</services>";
            VespaModelTester tester = new VespaModelTester();
            tester.addHosts(2);
            try {
                tester.createModel(xmlWithNodes, true);
                fail("ZooKeeper should not be allowed on combined clusters");
            }
            catch (IllegalArgumentException e) {
                assertEquals("A combined cluster cannot run ZooKeeper", e.getMessage());
            }
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

        int numberOfHosts = 67;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        // Check container cluster
        assertEquals(1, model.getContainerClusters().size());
        Set<HostResource> containerHosts = model.getContainerClusters().get("foo").getContainers().stream()
                                                .map(Container::getHost)
                                                .collect(Collectors.toSet());
        assertEquals(10, containerHosts.size());

        // Check admin clusters
        Admin admin = model.getAdmin();
        Set<HostResource> slobrokHosts = admin.getSlobroks().stream().map(Slobrok::getHost).collect(Collectors.toSet());
        assertEquals(3, slobrokHosts.size());
        assertTrue("Slobroks are assigned from container nodes", containerHosts.containsAll(slobrokHosts));
        assertTrue("Logserver is assigned from container nodes", containerHosts.contains(admin.getLogserver().getHost()));
        assertEquals("No in-cluster config servers in a hosted environment", 0, admin.getConfigservers().size());
        assertEquals("Dedicated admin cluster controllers when hosted", 3, admin.getClusterControllers().getContainers().size());

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        assertNull("No own cluster controllers when hosted", cluster.getClusterControllers());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(9, cluster.getRootGroup().getSubgroups().size());
        assertEquals("0", cluster.getRootGroup().getSubgroups().get(0).getIndex());
        assertEquals(3, cluster.getRootGroup().getSubgroups().get(0).getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertEquals("node-1-3-10-57", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getConfigId(), is("bar/storage/1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getDistributionKey(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getConfigId(), is("bar/storage/2"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/3"));
        assertEquals("node-1-3-10-54", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getDistributionKey(), is(4));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getConfigId(), is("bar/storage/4"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getDistributionKey(), is(5));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getConfigId(), is("bar/storage/5"));
        // ...
        assertEquals("node-1-3-10-51", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
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
        assertNull("No own cluster controllers when hosted", cluster.getClusterControllers());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(27, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("baz/storage/0"));
        assertEquals("node-1-3-10-27", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("baz/storage/1"));
        assertEquals("node-1-3-10-26", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        // ...
        assertEquals("node-1-3-10-25", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
        // ...
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getIndex(), is("26"));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().get(0).getDistributionKey(), is(26));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().get(0).getConfigId(), is("baz/storage/26"));
    }

    @Test
    public void testUsingNodesAndGroupCountAttributesWithoutDedicatedClusterControllers() {
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
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        // Check container cluster
        assertEquals(1, model.getContainerClusters().size());
        Set<HostResource> containerHosts = model.getContainerClusters().get("foo").getContainers().stream()
                                                .map(Container::getHost)
                                                .collect(Collectors.toSet());
        assertEquals(10, containerHosts.size());

        // Check admin clusters
        Admin admin = model.getAdmin();
        Set<HostResource> slobrokHosts = admin.getSlobroks().stream().map(Slobrok::getHost).collect(Collectors.toSet());
        assertEquals(3, slobrokHosts.size());
        assertTrue("Slobroks are assigned from container nodes", containerHosts.containsAll(slobrokHosts));
        assertTrue("Logserver is assigned from container nodes", containerHosts.contains(admin.getLogserver().getHost()));
        assertEquals("No in-cluster config servers in a hosted environment", 0, admin.getConfigservers().size());
        assertEquals("No admin cluster controller when multitenant", null, admin.getClusterControllers());

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ClusterControllerContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("node-1-3-10-54", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("node-1-3-10-51", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("node-1-3-10-48", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(9, cluster.getRootGroup().getSubgroups().size());
        assertEquals("0", cluster.getRootGroup().getSubgroups().get(0).getIndex());
        assertEquals(3, cluster.getRootGroup().getSubgroups().get(0).getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertEquals("node-1-3-10-54", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getConfigId(), is("bar/storage/1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getDistributionKey(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getConfigId(), is("bar/storage/2"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/3"));
        assertEquals("node-1-3-10-51", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getDistributionKey(), is(4));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getConfigId(), is("bar/storage/4"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getDistributionKey(), is(5));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getConfigId(), is("bar/storage/5"));
        // ...
        assertEquals("node-1-3-10-48", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
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
        assertEquals("node-1-3-10-27", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("node-1-3-10-26", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("node-1-3-10-25", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(27, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("baz/storage/0"));
        assertEquals("node-1-3-10-27", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("baz/storage/1"));
        assertEquals("node-1-3-10-26", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        // ...
        assertEquals("node-1-3-10-25", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
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

        int numberOfHosts = 21;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        ClusterControllerContainerCluster clusterControllers = model.getAdmin().getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("cluster-controllers", clusterControllers.getName());
        assertEquals("node-1-3-10-03", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("node-1-3-10-02", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("node-1-3-10-01", clusterControllers.getContainers().get(2).getHostName());

        // Check content cluster
        ContentCluster cluster = model.getContentClusters().get("bar");
        assertNull(cluster.getClusterControllers());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(8, cluster.getRootGroup().getSubgroups().size());
        assertEquals(8, cluster.distributionBits());
        // first group
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertEquals("node-1-3-10-11", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        // second group
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/1"));
        assertEquals("node-1-3-10-10", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        // ... last group
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getIndex(), is("7"));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getDistributionKey(), is(7));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getConfigId(), is("bar/storage/7"));
        assertEquals("node-1-3-10-04", cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getHostName());
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
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ClusterControllerContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals("We get the closest odd number", 3, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("node-1-3-10-08", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("node-1-3-10-06", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("node-1-3-10-04", clusterControllers.getContainers().get(2).getHostName());
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
        VespaModel model = tester.createModel(services, true, "node-1-3-10-09");
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        assertEquals("Includes retired node", 1+3, model.getAdmin().getSlobroks().size());
        assertEquals("node-1-3-10-10", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("node-1-3-10-08", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("node-1-3-10-07", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("Included in addition because it is retired", "node-1-3-10-09", model.getAdmin().getSlobroks().get(3).getHostName());
    }

    @Test
    public void testSlobroksClustersAreExpandedToIncludeRetiredNodesWhenRetiredComesLast() {
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
        VespaModel model = tester.createModel(services, true, "node-1-3-10-01", "node-1-3-10-02");
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        // Check slobroks clusters
        assertEquals("Includes retired node", 3+2, model.getAdmin().getSlobroks().size());
        assertEquals("node-1-3-10-10", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("node-1-3-10-09", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("node-1-3-10-08", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("Included in addition because it is retired", "node-1-3-10-02", model.getAdmin().getSlobroks().get(3).getHostName());
        assertEquals("Included in addition because it is retired", "node-1-3-10-01", model.getAdmin().getSlobroks().get(4).getHostName());
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
        VespaModel model = tester.createModel(services, true, "node-1-3-10-12", "node-1-3-10-03", "node-1-3-10-02");
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        // ... from cluster default
        assertEquals("Includes retired node", 3+3, model.getAdmin().getSlobroks().size());
        assertEquals("node-1-3-10-13", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("node-1-3-10-11", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("Included in addition because it is retired", "node-1-3-10-12", model.getAdmin().getSlobroks().get(2).getHostName());
        // ... from cluster bar
        assertEquals("node-1-3-10-01", model.getAdmin().getSlobroks().get(3).getHostName());
        assertEquals("Included in addition because it is retired", "node-1-3-10-03", model.getAdmin().getSlobroks().get(4).getHostName());
        assertEquals("Included in addition because it is retired", "node-1-3-10-02", model.getAdmin().getSlobroks().get(5).getHostName());
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
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(1, clusterControllers.getContainers().size());
    }

    @Test
    public void testDedicatedClusterControllers() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <content version='1.0' id='foo'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' />" +
                "  </content>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' />" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 7;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services);
        assertEquals(7, model.getRoot().hostSystem().getHosts().size());

        // Check cluster controllers
        assertNull(model.getContentClusters().get("foo").getClusterControllers());
        assertNull(model.getContentClusters().get("bar").getClusterControllers());
        ClusterControllerContainerCluster clusterControllers = model.getAdmin().getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("cluster-controllers", clusterControllers.getName());
        clusterControllers.getContainers().stream().map(ClusterControllerContainer::getHost).forEach(host -> {
            assertTrue(host.spec().membership().get().cluster().isStateful());
            assertEquals(ClusterSpec.Type.admin, host.spec().membership().get().cluster().type());
        });
    }

    @Test
    public void testLogserverContainerWhenDedicatedLogserver() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'>" +
                        "    <logservers>" +
                        "      <nodes count='1' dedicated='true'/>" +
                        "    </logservers>" +
                        "  </admin>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='1'/>" +
                        "  </container>" +
                        "</services>";
        boolean useDedicatedNodeForLogserver = false;
        testContainerOnLogserverHost(services, useDedicatedNodeForLogserver);
    }

    @Test
    public void testImplicitLogserverContainer() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='1'/>" +
                        "  </container>" +
                        "</services>";
        boolean useDedicatedNodeForLogserver = true;
        testContainerOnLogserverHost(services, useDedicatedNodeForLogserver);
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
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

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
                        "  <container version='1.0' id='container'>" +
                        "     <search/>" +
                        "     <nodes count='2'/>" +
                        "  </container>" +
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

        int numberOfHosts = 6;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false);
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(4, cluster.redundancy().effectiveInitialRedundancy());
        assertEquals(4, cluster.redundancy().effectiveFinalRedundancy());
        assertEquals(4, cluster.redundancy().effectiveReadyCopies());
        assertEquals(4, cluster.getSearch().getIndexed().getDispatchSpec().getGroups().size());
        assertEquals(4, cluster.getSearch().getIndexed().getSearchableCopies());
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
    public void testUsingNodesAndGroupCountAttributesAndGettingJustOneNode() {
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
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false);
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        ContentCluster cluster = model.getContentClusters().get("bar");
        ClusterControllerContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(1, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("node-1-3-10-01", clusterControllers.getContainers().get(0).getHostName());
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
    public void testRequiringMoreNodesThanAreAvailable() {
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

    @Test(expected = IllegalArgumentException.class)
    public void testRequiredNodesAndDedicatedClusterControllers() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <content version='1.0' id='foo'>" +
                "     <redundancy>1</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' required='true'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 4; // needs 2 for foo and 3 for cluster controllers.
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        tester.createModel(services, false);
    }

    @Test
    public void testExclusiveNodes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "<container version='1.0' id='container'>" +
                "      <nodes count='2' exclusive='true'/>" +
                "   </container>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='3' exclusive='true'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 5;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false);
        model.hostSystem().getHosts().forEach(host -> assertTrue(host.spec().membership().get().cluster().isExclusive()));
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
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

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
    public void testRequestingSpecificNodeResources() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "   <admin version='4.0'>" +
                "      <logservers>" +
                "         <nodes count='1' dedicated='true'>" +
                "            <resources vcpu='0.1' memory='0.2Gb' disk='300Gb' disk-speed='slow'/>" +
                "         </nodes>" +
                "      </logservers>" +
                "      <slobroks>" +
                "         <nodes count='2' dedicated='true'>" +
                "            <resources vcpu='0.1' memory='0.3Gb' disk='1Gb' bandwidth='500Mbps'/>" +
                "         </nodes>" +
                "      </slobroks>" +
                "   </admin>" +
                "   <container version='1.0' id='container'>" +
                "      <nodes count='4'>" +
                "         <resources vcpu='12' memory='10Gb' disk='30Gb'/>" +
                "      </nodes>" +
                "   </container>" +
                "   <content version='1.0' id='foo'>" +
                "      <documents>" +
                "        <document type='type1' mode='index'/>" +
                "      </documents>" +
                "      <nodes count='5'>" +
                "         <resources vcpu='8' memory='200Gb' disk='1Pb'/>" +
                "      </nodes>" +
                "   </content>" +
                "   <content version='1.0' id='bar'>" +
                "      <documents>" +
                "        <document type='type1' mode='index'/>" +
                "      </documents>" +
                "      <nodes count='6'>" +
                "         <resources vcpu='10' memory='64Gb' disk='200Gb'/>" +
                "      </nodes>" +
                "   </content>" +
                "</services>";

        int totalHosts = 18;
        VespaModelTester tester = new VespaModelTester();
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(new NodeResources(0.1, 0.2, 300, 0.3, NodeResources.DiskSpeed.slow), 1);// Logserver
        tester.addHosts(new NodeResources(0.1, 0.3, 1, 0.5), 2); // Slobrok
        tester.addHosts(new NodeResources(12, 10, 30, 0.3), 4); // Container
        tester.addHosts(new NodeResources(8, 200, 1000000, 0.3), 5); // Content-foo
        tester.addHosts(new NodeResources(10, 64, 200, 0.3), 6); // Content-bar
        VespaModel model = tester.createModel(services, true, 0);
        assertEquals(totalHosts, model.getRoot().hostSystem().getHosts().size());
    }

    @Test
    public void testRequestingRangesMin() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "   <container version='1.0' id='container'>" +
                "      <nodes count='[4, 6]'>" +
                "         <resources vcpu='[11.5, 13.5]' memory='[10Gb, 100Gb]' disk='[30Gb, 1Tb]'/>" +
                "      </nodes>" +
                "   </container>" +
                "   <content version='1.0' id='foo'>" +
                "      <documents>" +
                "        <document type='type1' mode='index'/>" +
                "      </documents>" +
                "      <nodes count='[6, 20]' groups='[3,4]'>" +
                "         <resources vcpu='8' memory='200Gb' disk='1Pb'/>" +
                "      </nodes>" +
                "   </content>" +
                "</services>";

        int totalHosts = 10;
        VespaModelTester tester = new VespaModelTester();
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(new NodeResources(11.5, 10, 30, 0.3), 6);
        tester.addHosts(new NodeResources(85, 200, 1000_000_000, 0.3), 20);
        VespaModel model = tester.createModel(services, true);
        assertEquals(totalHosts, model.getRoot().hostSystem().getHosts().size());
    }

    @Test
    public void testRequestingRangesMax() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "   <container version='1.0' id='container'>" +
                "      <nodes count='[4, 6]'>" +
                "         <resources vcpu='[11.5, 13.5]' memory='[10Gb, 100Gb]' disk='[30Gb, 1Tb]'/>" +
                "      </nodes>" +
                "   </container>" +
                "   <content version='1.0' id='foo'>" +
                "      <documents>" +
                "        <document type='type1' mode='index'/>" +
                "      </documents>" +
                "      <nodes count='[6, 20]' groups='[3,4]'>" +
                "         <resources vcpu='8' memory='200Gb' disk='1Pb'/>" +
                "      </nodes>" +
                "   </content>" +
                "</services>";

        int totalHosts = 26;
        VespaModelTester tester = new VespaModelTester();
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(new NodeResources(13.5, 100, 1000, 0.3), 6);
        tester.addHosts(new NodeResources(85, 200, 1000_000_000, 0.3), 20);
        VespaModel model = tester.createModel(services, true, true);
        assertEquals(totalHosts, model.getRoot().hostSystem().getHosts().size());
    }

    @Test
    public void testContainerOnly() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<container version='1.0'>" +
                        "  <search/>" +
                        "  <nodes count='3'/>" +
                        "</container>";
        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());
        assertEquals(3, model.getContainerClusters().get("container").getContainers().size());
        assertNotNull(model.getAdmin().getLogserver());
        assertEquals(3, model.getAdmin().getSlobroks().size());
    }

    @Test
    public void testJvmArgs() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<container version='1.0'>" +
                        "  <search/>" +
                        "  <nodes jvmargs='xyz' count='3'/>" +
                        "</container>";
        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());
        assertEquals("xyz", model.getContainerClusters().get("container").getContainers().get(0).getAssignedJvmOptions());
    }

    @Test
    public void testJvmOptions() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<container version='1.0'>" +
                        "  <search/>" +
                        "  <nodes jvm-options='xyz' count='3'/>" +
                        "</container>";
        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());
        assertEquals("xyz", model.getContainerClusters().get("container").getContainers().get(0).getAssignedJvmOptions());
    }

    @Test
    public void testJvmOptionsOverridesJvmArgs() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<container version='1.0'>" +
                        "  <search/>" +
                        "  <nodes jvm-options='xyz' jvmargs='abc' count='3'/>" +
                        "</container>";
        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        try {
            tester.createModel(services, true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("You have specified both jvm-options='xyz' and deprecated jvmargs='abc'. Merge jvmargs into jvm-options.", e.getMessage());
        }
    }

    @Test
    public void testUsingHostaliasWithProvisioner() {
        String services =
                        "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "<admin version='2.0'>" +
                        "  <adminserver hostalias='node1'/>\n"+
                        "</admin>\n" +
                        "<container id='mydisc' version='1.0'>" +
                        "  <handler id='myHandler'>" +
                        "    <component id='injected' />" +
                        "  </handler>" +
                        "  <nodes>" +
                        "    <node hostalias='node1'/>" +
                        "  </nodes>" +
                        "</container>" +
                        "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(false);
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertEquals(1, model.getRoot().hostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
    }

    @Test
    public void testThatStandaloneSyntaxWorksOnHostedVespa() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<container id='foo' version='1.0'>" +
                "  <http>" +
                "    <server id='server1' port='" + getDefaults().vespaWebServicePort() + "' />" +
                "  </http>" +
                "</container>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(2);
        VespaModel model = tester.createModel(services, true);
        assertEquals(2, model.getHosts().size());
        assertEquals(1, model.getContainerClusters().size());
        assertEquals(2, model.getContainerClusters().get("foo").getContainers().size());
    }

    @Test
    public void testThatStandaloneSyntaxOnHostedVespaRequiresDefaultPort() {
        try {
            String services =
                    "<?xml version='1.0' encoding='utf-8' ?>" +
                    "<container id='foo' version='1.0'>" +
                    "  <http>" +
                    "    <server id='server1' port='8095' />" +
                    "  </http>" +
                    "</container>";
            VespaModelTester tester = new VespaModelTester();
            tester.addHosts(1);
            VespaModel model = tester.createModel(services, true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            // Success
            assertEquals("Illegal port 8095 in http server 'server1': Port must be set to " +
                         getDefaults().vespaWebServicePort(), e.getMessage());
        }
    }

    @Test
    public void testThatStandaloneSyntaxWorksOnHostedManuallyDeployed() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "   <admin version='2.0'>" +
                "      <adminserver hostalias='node1'/>" +
                "   </admin>"  +
                "   <container id='foo' version='1.0'>" +
                "      <nodes>" +
                "         <node hostalias='node1'/>" +
                "         <node hostalias='node2'/>" +
                "      </nodes>" +
                "   </container>" +
                "   <content id='bar' version='1.0'>" +
                "      <documents>" +
                "         <document type='type1' mode='index'/>" +
                "      </documents>" +
                "      <redundancy>2</redundancy>" +
                "      <nodes>" +
               "          <group>" +
                "            <node distribution-key='0' hostalias='node3'/>" +
                "            <node distribution-key='1' hostalias='node4'/>" +
               "          </group>" +
                "      </nodes>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(true);
        tester.addHosts(4);
        VespaModel model = tester.createModel(new Zone(Environment.dev, RegionName.from("us-central-1")), services, true);
        assertEquals("We get 1 node per cluster and no admin node apart from the dedicated cluster controller", 3, model.getHosts().size());
        assertEquals(1, model.getContainerClusters().size());
        assertEquals(1, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().countNodes());
        assertEquals(1, model.getAdmin().getClusterControllers().getContainers().size());
    }

    /** Deploying an application with "nodes count" standalone should give a single-node deployment */
    @Test
    public void testThatHostedSyntaxWorksOnStandalone() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <search/>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <content version='1.0'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(false);
        tester.addHosts(3);
        VespaModel model = tester.createModel(xmlWithNodes, true);

        assertEquals("Nodes in container cluster", 1, model.getContainerClusters().get("container1").getContainers().size());
        assertEquals("Nodes in content cluster (downscaled)", 1, model.getContentClusters().get("content").getRootGroup().getNodes().size());
        model.getConfig(new StorStatusConfig.Builder(), "default");
    }

    @Test
    public void testNoNodeTagMeansTwoNodes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <container id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "  </container>" +
                "  <content version='1.0' id='bar'>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "  </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(3);
        VespaModel model = tester.createModel(services, true);
        assertEquals(3, model.getRoot().hostSystem().getHosts().size());
        assertEquals(2, model.getAdmin().getSlobroks().size());
        assertEquals(2, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().countNodes());
    }

    @Test
    public void testNoNodeTagMeansTwoNodesNoContent() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <container id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "  </container>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(2);
        VespaModel model = tester.createModel(services, true);
        assertEquals(2, model.getRoot().hostSystem().getHosts().size());
        assertEquals(2, model.getAdmin().getSlobroks().size());
        assertEquals(2, model.getContainerClusters().get("foo").getContainers().size());
    }

    @Test
    public void testNoNodeTagMeans1NodeNonHosted() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <container id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "  </container>" +
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
        assertEquals(1, model.getRoot().hostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
        assertEquals(1, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().recursiveGetNodes().size());
    }

    @Test
    public void testSingleNodeNonHosted() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <container id='foo' version='1.0'>" +
                "    <search/>" +
                "    <document-api/>" +
                "    <nodes><node hostalias='foo'/></nodes>"+
                "  </container>" +
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
        assertEquals(1, model.getRoot().hostSystem().getHosts().size());
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
        "   <container id='default' version='1.0'>" +
        "     <search/>" +
        "     <nodes>" +
        "       <node hostalias='node1'/>" +
        "     </nodes>" +
        "   </container>" +
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
        assertEquals(1, model.getRoot().hostSystem().getHosts().size());
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
                "    <nodes jvm-options=\"-Xms512m -Xmx512m\">\n" +
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
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(1));
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
                "    <nodes jvm-options=\"-Xms512m -Xmx512m\">\n" +
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
        assertEquals(3, model.getRoot().hostSystem().getHosts().size());
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
                "   <container id='default' version='1.0'>" +
                "     <search/>" +
                "     <nodes>" +
                "       <node hostalias='node1'/>" +
                "     </nodes>" +
                "   </container>" +
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
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(1));
        ContentCluster content = model.getContentClusters().get("storage");
        assertEquals(2, content.getRootGroup().getNodes().size());
        ContainerCluster controller = content.getClusterControllers();
        assertEquals(1, controller.getContainers().size());
    }

    @Test
    public void testStatefulProperty() {
        String servicesXml =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='qrs'>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <container version='1.0' id='zk'>" +
                "     <zookeeper/>" +
                "     <nodes count='3'/>" +
                "  </container>" +
                "  <content version='1.0' id='content'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.dedicatedClusterControllerCluster(false);
        tester.addHosts(6);
        VespaModel model = tester.createModel(servicesXml, true);

        Map<String, Boolean> tests = Map.of("qrs", false,
                                            "zk", true,
                                            "content", true);
        Map<String, List<HostResource>> hostsByCluster = model.hostSystem().getHosts().stream()
                                                              .collect(Collectors.groupingBy(h -> h.spec().membership().get().cluster().id().value()));
        tests.forEach((clusterId, stateful) -> {
            List<HostResource> hosts = hostsByCluster.getOrDefault(clusterId, List.of());
            assertFalse("Hosts are provisioned for '" + clusterId + "'", hosts.isEmpty());
            assertEquals("Hosts in cluster '" + clusterId + "' are " + (stateful ? "" : "not ") + "stateful",
                         stateful,
                         hosts.stream().allMatch(h -> h.spec().membership().get().cluster().isStateful()));
        });
    }

    @Test
    public void containerWithZooKeeperSuboptimalNodeCountDuringRetirement() {
        String servicesXml =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='zk'>" +
                "     <zookeeper/>" +
                "     <nodes count='4'/>" + // (3 + 1 retired)
                "  </container>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(4);
        VespaModel model = tester.createModel(servicesXml, true, "node-1-3-10-01");
        ApplicationContainerCluster cluster = model.getContainerClusters().get("zk");
        assertEquals(1, cluster.getContainers().stream().filter(Container::isRetired).count());
        assertEquals(3, cluster.getContainers().stream().filter(c -> !c.isRetired()).count());
    }

    @Test
    public void containerWithZooKeeperJoiningServers() {
        Function<Integer, String> servicesXml = (nodeCount) -> {
            return "<?xml version='1.0' encoding='utf-8' ?>" +
                   "<services>" +
                   "  <container version='1.0' id='zk'>" +
                   "     <zookeeper/>" +
                   "     <nodes count='" + nodeCount + "'/>" +
                   "  </container>" +
                   "</services>";
        };
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(5);
        VespaModel model = tester.createModel(servicesXml.apply(3), true);

        {
            ApplicationContainerCluster cluster = model.getContainerClusters().get("zk");
            ZookeeperServerConfig.Builder config = new ZookeeperServerConfig.Builder();
            cluster.getContainers().forEach(c -> c.getConfig(config));
            cluster.getConfig(config);
            assertTrue("Initial servers are not joining", config.build().server().stream().noneMatch(ZookeeperServerConfig.Server::joining));
        }
        {
            VespaModel nextModel = tester.createModel(Zone.defaultZone(), servicesXml.apply(5), true, false, 0, Optional.of(model), new DeployState.Builder());
            ApplicationContainerCluster cluster = nextModel.getContainerClusters().get("zk");
            ZookeeperServerConfig.Builder config = new ZookeeperServerConfig.Builder();
            cluster.getContainers().forEach(c -> c.getConfig(config));
            cluster.getConfig(config);
            assertEquals("New nodes are joining",
                         Map.of(0, false,
                                1, false,
                                2, false,
                                3, true,
                                4, true),
                         config.build().server().stream().collect(Collectors.toMap(ZookeeperServerConfig.Server::id,
                                                                                   ZookeeperServerConfig.Server::joining)));
        }
    }

    private VespaModel createNonProvisionedMultitenantModel(String services) {
        return createNonProvisionedModel(true, null, services);
    }

    private VespaModel createNonProvisionedModel(boolean multitenant, String hosts, String services) {
        VespaModelCreatorWithMockPkg modelCreatorWithMockPkg = new VespaModelCreatorWithMockPkg(hosts, services, ApplicationPackageUtils.generateSearchDefinition("type1"));
        ApplicationPackage appPkg = modelCreatorWithMockPkg.appPkg;
        DeployState deployState = new DeployState.Builder().applicationPackage(appPkg).
                properties((new TestProperties()).setMultitenant(multitenant)).
                build();
        return modelCreatorWithMockPkg.create(false, deployState);
    }

    private int physicalMemoryPercentage(ContainerCluster cluster) {
        QrStartConfig.Builder b = new QrStartConfig.Builder();
        cluster.getConfig(b);
        return b.build().jvm().heapSizeAsPercentageOfPhysicalMemory();
    }

    private long protonMemorySize(ContentCluster cluster) {
        ProtonConfig.Builder b  = new ProtonConfig.Builder();
        cluster.getSearch().getIndexed().getSearchNode(0).getConfig(b);
        return b.build().hwinfo().memory().size();
    }

    @Test
    public void require_that_proton_config_is_tuned_based_on_node_resources() {
         String services = joinLines("<?xml version='1.0' encoding='utf-8' ?>",
                 "<services>",
                 "  <content version='1.0' id='test'>",
                 "     <documents>",
                 "       <document type='type1' mode='index'/>",
                 "     </documents>",
                 "     <nodes count='2'>",
                 "       <resources vcpu='1' memory='3Gb' disk='9Gb' bandwidth='5Gbps' disk-speed='slow'/>",
                 "     </nodes>",
                 "  </content>",
                 "</services>");

         VespaModelTester tester = new VespaModelTester();
         tester.addHosts(new NodeResources(1, 3, 10, 5, NodeResources.DiskSpeed.slow), 5);
         VespaModel model = tester.createModel(services, true, 0);
         ContentSearchCluster cluster = model.getContentClusters().get("test").getSearch();
         assertEquals(2, cluster.getSearchNodes().size());
         assertEquals(40, getProtonConfig(cluster, 0).hwinfo().disk().writespeed(), 0.001);
         assertEquals(40, getProtonConfig(cluster, 1).hwinfo().disk().writespeed(), 0.001);
    }

    private static ProtonConfig getProtonConfig(ContentSearchCluster cluster, int searchNodeIdx) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        List<SearchNode> searchNodes = cluster.getSearchNodes();
        assertTrue(searchNodeIdx < searchNodes.size());
        searchNodes.get(searchNodeIdx).getConfig(builder);
        return new ProtonConfig(builder);
    }

    @Test
    public void require_that_config_override_and_explicit_proton_tuning_and_resource_limits_have_precedence_over_default_node_resource_tuning() {
        String services = joinLines("<?xml version='1.0' encoding='utf-8' ?>",
                "<services>",
                "  <content version='1.0' id='test'>",
                "    <config name='vespa.config.search.core.proton'>",
                "      <flush><memory><maxtlssize>2000</maxtlssize></memory></flush>",
                "    </config>",
                "    <documents>",
                "      <document type='type1' mode='index'/>",
                "    </documents>",
                "    <nodes count='1'>",
                "      <resources vcpu='1' memory='128Gb' disk='100Gb'/>",
                "    </nodes>",
                "    <engine>",
                "      <proton>",
                "        <resource-limits>",
                "          <memory>0.92</memory>",
                "        </resource-limits>",
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
        tester.addHosts(new NodeResources(1, 3, 10, 1), 4);
        tester.addHosts(new NodeResources(1, 128, 100, 0.3), 1);
        VespaModel model = tester.createModel(services, true, 0);
        ContentSearchCluster cluster = model.getContentClusters().get("test").getSearch();
        ProtonConfig cfg = getProtonConfig(model, cluster.getSearchNodes().get(0).getConfigId());
        assertEquals(2000, cfg.flush().memory().maxtlssize()); // from config override
        assertEquals(1000, cfg.flush().memory().maxmemory()); // from explicit tuning
        assertEquals((long) ((128 - reservedMemoryGb) * GB / 8), cfg.flush().memory().each().maxmemory()); // from default node flavor tuning
        assertEquals(0.92, cfg.writefilter().memorylimit(), 0.0001); // from explicit resource-limits
    }

    private static ProtonConfig getProtonConfig(VespaModel model, String configId) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        model.getConfig(builder, configId);
        return new ProtonConfig(builder);
    }

    // Tests that a container is allocated on logserver host and that
    // it is able to get config
    private void testContainerOnLogserverHost(String services, boolean useDedicatedNodeForLogserver) {
        int numberOfHosts = 2;
        VespaModelTester tester = new VespaModelTester();
        tester.useDedicatedNodeForLogserver(useDedicatedNodeForLogserver);
        tester.addHosts(numberOfHosts);

        VespaModel model = tester.createModel(Zone.defaultZone(), services, true);
        assertThat(model.getRoot().hostSystem().getHosts().size(), is(numberOfHosts));

        Admin admin = model.getAdmin();
        Logserver logserver = admin.getLogserver();
        HostResource hostResource = logserver.getHostResource();
        assertNotNull(hostResource.getService("logserver"));
        String containerServiceType = ContainerServiceType.LOGSERVER_CONTAINER.serviceName;
        assertNotNull(hostResource.getService(containerServiceType));

        // Test that the container gets config
        String configId = admin.getLogserver().getHostResource().getService(containerServiceType).getConfigId();
        ApplicationMetadataConfig.Builder builder = new ApplicationMetadataConfig.Builder();
        model.getConfig(builder, configId);
        ApplicationMetadataConfig cfg = new ApplicationMetadataConfig(builder);
        assertEquals(1, cfg.generation());

        LogdConfig.Builder logdConfigBuilder = new LogdConfig.Builder();
        model.getConfig(logdConfigBuilder, configId);
        LogdConfig logdConfig = new LogdConfig(logdConfigBuilder);
        // Logd should use logserver (forward logs to it)
        assertTrue(logdConfig.logserver().use());
    }

    private static void assertProvisioned(int nodeCount, ClusterSpec.Id id, ClusterSpec.Id combinedId,
                                          ClusterSpec.Type type, VespaModel model) {
        assertEquals("Nodes in cluster " + id + " with type " + type + (combinedId != null ? ", combinedId " + combinedId : ""), nodeCount,
                     model.hostSystem().getHosts().stream()
                          .map(h -> h.spec().membership().get().cluster())
                          .filter(spec -> spec.id().equals(id) && spec.type().equals(type) && spec.combinedId().equals(Optional.ofNullable(combinedId)))
                          .count());
    }

    private static void assertProvisioned(int nodeCount, ClusterSpec.Id id, ClusterSpec.Type type, VespaModel model) {
        assertProvisioned(nodeCount, id, null, type, model);
    }

}
