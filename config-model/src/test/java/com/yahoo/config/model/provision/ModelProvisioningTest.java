// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
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
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
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
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.vespa.model.test.VespaModelTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.config.provision.NodeResources.Architecture;
import static com.yahoo.config.provision.NodeResources.DiskSpeed;
import static com.yahoo.config.provision.NodeResources.StorageType;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.model.Host.memoryOverheadGb;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.GiB;
import static com.yahoo.vespa.model.test.utils.ApplicationPackageUtils.generateSchemas;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
                        "  <nodes count='2' preload='lib/blablamalloc.so'>" +
                        "    <jvm allocated-memory='45%' gc-options='-XX:+UseParNewGC' options='-Xlog:gc' />" +
                        "  </nodes>" +
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
                + " <host name='myhost6'>"
                + "  <alias>node6</alias>"
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

        assertEquals("-Xlog:gc", mydisc2.getContainers().get(0).getJvmOptions());
        assertEquals("-Xlog:gc", mydisc2.getContainers().get(1).getJvmOptions());
        assertEquals("lib/blablamalloc.so", mydisc2.getContainers().get(0).getPreLoad());
        assertEquals("lib/blablamalloc.so", mydisc2.getContainers().get(1).getPreLoad());
        assertEquals(45, mydisc2.getMemoryPercentage().get().ofContainerAvailable());
        assertEquals(Optional.of("-XX:+UseParNewGC"), mydisc2.getJvmGCOptions());
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        mydisc2.getConfig(qrStartBuilder);
        QrStartConfig qrsStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals(45, qrsStartConfig.jvm().heapSizeAsPercentageOfPhysicalMemory());
        
        HostSystem hostSystem = model.hostSystem();
        assertTrue(hostNameExists(hostSystem, "myhost0"));
        assertTrue(hostNameExists(hostSystem, "myhost1"));
        assertTrue(hostNameExists(hostSystem, "myhost2"));
        assertFalse(hostNameExists(hostSystem, "Nope"));
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
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("bar.indexing"));
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
                "  <container version='1.0' id='container2'>" +
                "     <search/>" +
                "     <nodes count='1'>" +
                "       <resources vcpu='10' memory='100Gb' disk='1Tb'/>" +
                "     </nodes>" +
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
        tester.addHosts(new NodeResources(20, 200, 2000, 1.0), 1);
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("container1", "container2"));

        assertEquals(2, model.getContentClusters().get("content1").getRootGroup().getNodes().size(), "Nodes in content1");
        assertEquals(1, model.getContainerClusters().get("container1").getContainers().size(), "Nodes in container1");
        assertEquals(2, model.getContentClusters().get("content").getRootGroup().getNodes().size(), "Nodes in cluster without ID");
        assertEquals(85, physicalMemoryPercentage(model.getContainerClusters().get("container1")), "Heap size for container1");
        assertEquals(85, physicalMemoryPercentage(model.getContainerClusters().get("container2")), "Heap size for container2");
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
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("container1"));

        assertEquals(1, model.hostSystem().getHosts().size());
        HostResource host = model.hostSystem().getHosts().iterator().next();

        assertTrue(host.spec().membership().isPresent());
        assertEquals("container", host.spec().membership().get().cluster().type().name());
        assertEquals("container1", host.spec().membership().get().cluster().id().value());
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
                        "     <nodes count='2'>" +
                        "       <resources vcpu='1' memory='3Gb' disk='9Gb'/>" +
                        "     </nodes>" +
                        "   </content>" +
                        "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(5);
        TestLogger logger = new TestLogger();
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("container1").deployLogger(logger));
        assertEquals(2, model.getContentClusters().get("content1").getRootGroup().getNodes().size(), "Nodes in content1");
        assertEquals(2, model.getContainerClusters().get("container1").getContainers().size(), "Nodes in container1");
        assertEquals(24, physicalMemoryPercentage(model.getContainerClusters().get("container1")), "Heap size is lowered with combined clusters");
        assertEquals(1876900708, protonMemorySize(model.getContentClusters().get("content1")), "Memory for proton is lowered to account for the jvm heap");
        assertProvisioned(0, ClusterSpec.Id.from("container1"), ClusterSpec.Type.container, model);
        assertProvisioned(2, ClusterSpec.Id.from("content1"), ClusterSpec.Id.from("container1"), ClusterSpec.Type.combined, model);
        var msgs = logger.msgs().stream().filter(m -> m.level().equals(Level.WARNING)).toList();
        assertEquals(1, msgs.size(), msgs.toString());
        assertEquals("Declaring combined cluster with <nodes of=\"...\"> is deprecated without replacement, " +
                     "and the feature will be removed in Vespa 9. Use separate container and content clusters instead",
                     msgs.get(0).message);
    }

    @Test
    public void testCombinedClusterWithJvmHeapSizeOverride() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "  <container version='1.0' id='container1'>" +
                        "     <search/>" +
                        "     <nodes of='content1'>" +
                        "      <jvm allocated-memory=\"30%\"/>" +
                        "     </nodes>" +
                        "  </container>" +
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
        tester.addHosts(5);
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("container1"));
        assertEquals(2, model.getContentClusters().get("content1").getRootGroup().getNodes().size(), "Nodes in content1");
        assertEquals(2, model.getContainerClusters().get("container1").getContainers().size(), "Nodes in container1");
        assertEquals(30, physicalMemoryPercentage(model.getContainerClusters().get("container1")), "Heap size is lowered with combined clusters");
        assertEquals((long) ((3 - memoryOverheadGb) * (Math.pow(1024, 3)) * (1 - 0.30)), protonMemorySize(model.getContentClusters()
                                                                                                                   .get("content1")), "Memory for proton is lowered to account for the jvm heap");
        assertProvisioned(0, ClusterSpec.Id.from("container1"), ClusterSpec.Type.container, model);
        assertProvisioned(2, ClusterSpec.Id.from("content1"), ClusterSpec.Id.from("container1"), ClusterSpec.Type.combined, model);
    }

    /** For comparison with the above */
    @Test
    public void testNonCombinedCluster() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "  <container version='1.0' id='container1'>" +
                        "     <search/>" +
                        "     <nodes count='2'/>" +
                        "  </container>" +
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
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("container1"));
        assertEquals(2, model.getContentClusters().get("content1").getRootGroup().getNodes().size(), "Nodes in content1");
        assertEquals(2, model.getContainerClusters().get("container1").getContainers().size(), "Nodes in container1");
        assertEquals(85, physicalMemoryPercentage(model.getContainerClusters().get("container1")), "Heap size is normal");
        assertEquals((long) ((3 - memoryOverheadGb) * (Math.pow(1024, 3))), protonMemorySize(model.getContentClusters().get("content1")), "Memory for proton is normal");
    }

    @Test
    public void testCombinedClusterWithJvmOptions() {
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <document-processing/>" +
                "     <nodes of='content1'>" +
                "       <jvm options='-Dtestoption=foo' />" +
                "     </nodes>" +
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
        tester.addHosts(5);
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("container1"));

        assertEquals(2, model.getContentClusters().get("content1").getRootGroup().getNodes().size(), "Nodes in content1");
        assertEquals(2, model.getContainerClusters().get("container1").getContainers().size(), "Nodes in container1");
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
        VespaModel model = tester.createModel(xmlWithNodes, true, deployStateWithClusterEndpoints("container1", "container2"));

        assertEquals(2, model.getContentClusters().get("content1").getRootGroup().getNodes().size(), "Nodes in content1");
        assertEquals(2, model.getContainerClusters().get("container1").getContainers().size(), "Nodes in container1");
        assertEquals(3, model.getContentClusters().get("content2").getRootGroup().getNodes().size(), "Nodes in content2");
        assertEquals(3, model.getContainerClusters().get("container2").getContainers().size(), "Nodes in container2");
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
        String xmlWithNodes =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "  <container version='1.0' id='container1'>" +
                        "     <search/>" +
                        "     <nodes of='content1'/>" +
                        "     <zookeeper />" +
                        "  </container>" +
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
        } catch (IllegalArgumentException e) {
            assertEquals("A combined cluster cannot run ZooKeeper", e.getMessage());
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
                "     <nodes count='27' groups='9' group-size='[2, 3]'/>" +
                "  </content>" +
                "  <content version='1.0' id='baz'>" +
                "     <redundancy>1</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='27' groups='27' group-size='1'/>" +
                "   </content>" +
                "</services>";

        int numberOfHosts = 67;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        // Check container cluster
        assertEquals(1, model.getContainerClusters().size());
        Set<HostResource> containerHosts = model.getContainerClusters().get("foo").getContainers().stream()
                                                .map(Container::getHost)
                                                .collect(Collectors.toSet());
        assertEquals(10, containerHosts.size());

        // Check admin clusters
        Admin admin = model.getAdmin();
        Set<HostResource> clusterControllerHosts = admin.getClusterControllers().getContainers()
                                                        .stream().map(cc -> cc.getHostResource()).collect(Collectors.toSet());
        Set<HostResource> slobrokHosts = admin.getSlobroks().stream().map(Slobrok::getHost).collect(Collectors.toSet());
        assertEquals(3, slobrokHosts.size());
        assertTrue(clusterControllerHosts.containsAll(slobrokHosts), "Slobroks are assigned on cluster controller nodes");
        assertTrue(containerHosts.contains(admin.getLogserver().getHost()), "Logserver is assigned from container nodes");
        assertEquals(0, admin.getConfigservers().size(), "No in-cluster config servers in a hosted environment");
        assertEquals(3, admin.getClusterControllers().getContainers().size(), "Dedicated admin cluster controllers when hosted");

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        List<StorageGroup> subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(9, subGroups.size());
        assertEquals("0", subGroups.get(0).getIndex());
        assertEquals(3, subGroups.get(0).getNodes().size());
        assertEquals(0, subGroups.get(0).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/0", subGroups.get(0).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-57", subGroups.get(0).getNodes().get(0).getHostName());
        assertEquals(1, subGroups.get(0).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/1", subGroups.get(0).getNodes().get(1).getConfigId());
        assertEquals(2, subGroups.get(0).getNodes().get(2).getDistributionKey());
        assertEquals("bar/storage/2", subGroups.get(0).getNodes().get(2).getConfigId());
        assertEquals("1", subGroups.get(1).getIndex());
        assertEquals(3, subGroups.get(1).getNodes().size());
        assertEquals(3, subGroups.get(1).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/3", subGroups.get(1).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-54", subGroups.get(1).getNodes().get(0).getHostName());
        assertEquals(4, subGroups.get(1).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/4", subGroups.get(1).getNodes().get(1).getConfigId());
        assertEquals(5, subGroups.get(1).getNodes().get(2).getDistributionKey());
        assertEquals("bar/storage/5", subGroups.get(1).getNodes().get(2).getConfigId());
        // ...
        assertEquals("node-1-3-50-51", subGroups.get(2).getNodes().get(0).getHostName());
        // ...
        assertEquals("8", subGroups.get(8).getIndex());
        assertEquals(3, subGroups.get(8).getNodes().size());
        assertEquals(24, subGroups.get(8).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/24", subGroups.get(8).getNodes().get(0).getConfigId());
        assertEquals(25, subGroups.get(8).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/25", subGroups.get(8).getNodes().get(1).getConfigId());
        assertEquals(26, subGroups.get(8).getNodes().get(2).getDistributionKey());
        assertEquals("bar/storage/26", subGroups.get(8).getNodes().get(2).getConfigId());

        cluster = model.getContentClusters().get("baz");
        subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(27, subGroups.size());
        assertEquals("0", subGroups.get(0).getIndex());
        assertEquals(1, subGroups.get(0).getNodes().size());
        assertEquals(0, subGroups.get(0).getNodes().get(0).getDistributionKey());
        assertEquals("baz/storage/0", subGroups.get(0).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-27", subGroups.get(0).getNodes().get(0).getHostName());
        assertEquals("1", subGroups.get(1).getIndex());
        assertEquals(1, subGroups.get(1).getNodes().size());
        assertEquals(1, subGroups.get(1).getNodes().get(0).getDistributionKey());
        assertEquals("baz/storage/1", subGroups.get(1).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-26", subGroups.get(1).getNodes().get(0).getHostName());
        // ...
        assertEquals("node-1-3-50-25", subGroups.get(2).getNodes().get(0).getHostName());
        // ...
        assertEquals("26", subGroups.get(26).getIndex());
        assertEquals(1, subGroups.get(26).getNodes().size());
        assertEquals(26, subGroups.get(26).getNodes().get(0).getDistributionKey());
        assertEquals("baz/storage/26", subGroups.get(26).getNodes().get(0).getConfigId());
    }

    @Test
    public void testUsingGroups() {
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
                "     <nodes count='30' groups='2'/>" +
                "  </content>" +
                "  <content version='1.0' id='baz'>" +
                "     <redundancy>1</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='30' groups='30'/>" +
                "   </content>" +
                "</services>";

        int numberOfHosts = 73;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        List<StorageGroup> subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals( 0, cluster.getRootGroup().getNodes().size());
        assertEquals( 2, subGroups.size());
        assertEquals(15, subGroups.get(0).getNodes().size());

        cluster = model.getContentClusters().get("baz");
        subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals( 0, cluster.getRootGroup().getNodes().size());
        assertEquals(30, subGroups.size());
        assertEquals( 1, subGroups.get(0).getNodes().size());
    }

    // Same as the test above but setting groupSize only
    @Test
    public void testUsingGroupSizeNotGroups() {
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
                "     <nodes count='30' group-size='[15, 30]'/>" +
                "  </content>" +
                "  <content version='1.0' id='baz'>" +
                "     <redundancy>1</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='30' group-size='1'/>" +
                "   </content>" +
                "</services>";

        int numberOfHosts = 73;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        List<StorageGroup> subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals( 0, cluster.getRootGroup().getNodes().size());
        assertEquals( 2, subGroups.size());
        assertEquals(15, subGroups.get(0).getNodes().size());

        cluster = model.getContentClusters().get("baz");
        subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals( 0, cluster.getRootGroup().getNodes().size());
        assertEquals(30, subGroups.size());
        assertEquals( 1, subGroups.get(0).getNodes().size());
    }

    @Test
    public void testIllegalGroupSize() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services>" +
                "  <admin version='4.0'/>" +
                "  <container version='1.0' id='foo'>" +
                "     <nodes count='2'/>" +
                "  </container>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='5' group-size='[2, --]'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 10;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        try {
            tester.createModel(services, true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In content cluster 'bar': Illegal group-size value: " +
                         "Expected a number or range on the form [min, max], but got '[2, --]': '--' is not an integer", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testSlobroksOnContainersIfNoContentClusters() {
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
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
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
        assertTrue(containerHosts.containsAll(slobrokHosts),
                   "Slobroks are assigned from container nodes");
        assertTrue(containerHosts.contains(admin.getLogserver().getHost()), "Logserver is assigned from container nodes");
        assertEquals(0, admin.getConfigservers().size(), "No in-cluster config servers in a hosted environment");
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

        int numberOfHosts = 67;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        // Check container cluster
        assertEquals(1, model.getContainerClusters().size());
        Set<HostResource> containerHosts = model.getContainerClusters().get("foo").getContainers().stream()
                                                .map(Container::getHost)
                                                .collect(Collectors.toSet());
        assertEquals(10, containerHosts.size());

        // Check admin clusters
        Admin admin = model.getAdmin();
        Set<HostResource> clusterControllerHosts = admin.getClusterControllers().getContainers()
                                                        .stream().map(cc -> cc.getHostResource()).collect(Collectors.toSet());
        Set<HostResource> slobrokHosts = admin.getSlobroks().stream().map(Slobrok::getHost).collect(Collectors.toSet());
        assertEquals(3, slobrokHosts.size());
        assertTrue(clusterControllerHosts.containsAll(slobrokHosts), "Slobroks are assigned on cluster controller nodes");
        assertTrue(containerHosts.contains(admin.getLogserver().getHost()), "Logserver is assigned from container nodes");
        assertEquals(0, admin.getConfigservers().size(), "No in-cluster config servers in a hosted environment");
        assertEquals(3, admin.getClusterControllers().getContainers().size());

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        List<StorageGroup> subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(9, subGroups.size());
        assertEquals("0", subGroups.get(0).getIndex());
        assertEquals(3, subGroups.get(0).getNodes().size());
        assertEquals(0, subGroups.get(0).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/0", subGroups.get(0).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-57", subGroups.get(0).getNodes().get(0).getHostName());
        assertEquals(1, subGroups.get(0).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/1", subGroups.get(0).getNodes().get(1).getConfigId());
        assertEquals(2, subGroups.get(0).getNodes().get(2).getDistributionKey());
        assertEquals("bar/storage/2", subGroups.get(0).getNodes().get(2).getConfigId());
        assertEquals("1", subGroups.get(1).getIndex());
        assertEquals(3, subGroups.get(1).getNodes().size());
        assertEquals(3, subGroups.get(1).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/3", subGroups.get(1).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-54", subGroups.get(1).getNodes().get(0).getHostName());
        assertEquals(4, subGroups.get(1).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/4", subGroups.get(1).getNodes().get(1).getConfigId());
        assertEquals(5, subGroups.get(1).getNodes().get(2).getDistributionKey());
        assertEquals("bar/storage/5", subGroups.get(1).getNodes().get(2).getConfigId());
        // ...
        assertEquals("node-1-3-50-51", subGroups.get(2).getNodes().get(0).getHostName());
        // ...
        assertEquals("8", subGroups.get(8).getIndex());
        assertEquals(3, subGroups.get(8).getNodes().size());
        assertEquals(24, subGroups.get(8).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/24", subGroups.get(8).getNodes().get(0).getConfigId());
        assertEquals(25, subGroups.get(8).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/25", subGroups.get(8).getNodes().get(1).getConfigId());
        assertEquals(26, subGroups.get(8).getNodes().get(2).getDistributionKey());
        assertEquals("bar/storage/26", subGroups.get(8).getNodes().get(2).getConfigId());

        cluster = model.getContentClusters().get("baz");
        subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(27, subGroups.size());
        assertEquals("0", subGroups.get(0).getIndex());
        assertEquals(1, subGroups.get(0).getNodes().size());
        assertEquals(0, subGroups.get(0).getNodes().get(0).getDistributionKey());
        assertEquals("baz/storage/0", subGroups.get(0).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-27", subGroups.get(0).getNodes().get(0).getHostName());
        assertEquals("1", subGroups.get(1).getIndex());
        assertEquals(1, subGroups.get(1).getNodes().size());
        assertEquals(1, subGroups.get(1).getNodes().get(0).getDistributionKey());
        assertEquals("baz/storage/1", subGroups.get(1).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-26", subGroups.get(1).getNodes().get(0).getHostName());
        // ...
        assertEquals("node-1-3-50-25", subGroups.get(2).getNodes().get(0).getHostName());
        // ...
        assertEquals("26", subGroups.get(26).getIndex());
        assertEquals(1, subGroups.get(26).getNodes().size());
        assertEquals(26, subGroups.get(26).getNodes().get(0).getDistributionKey());
        assertEquals("baz/storage/26", subGroups.get(26).getNodes().get(0).getConfigId());
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
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ClusterControllerContainerCluster clusterControllers = model.getAdmin().getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("cluster-controllers", clusterControllers.getName());
        assertEquals("node-1-3-50-03", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("node-1-3-50-02", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("node-1-3-50-01", clusterControllers.getContainers().get(2).getHostName());

        // Check content cluster
        ContentCluster cluster = model.getContentClusters().get("bar");
        List<StorageGroup> subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(8, subGroups.size());
        assertEquals(8, cluster.distributionBits());
        // first group
        assertEquals("0", subGroups.get(0).getIndex());
        assertEquals(1, subGroups.get(0).getNodes().size());
        assertEquals(0, subGroups.get(0).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/0", subGroups.get(0).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-11", subGroups.get(0).getNodes().get(0).getHostName());
        // second group
        assertEquals("1", subGroups.get(1).getIndex());
        assertEquals(1, subGroups.get(1).getNodes().size());
        assertEquals(1, subGroups.get(1).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/1", subGroups.get(1).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-10", subGroups.get(1).getNodes().get(0).getHostName());
        // ... last group
        assertEquals("7", subGroups.get(7).getIndex());
        assertEquals(1, subGroups.get(7).getNodes().size());
        assertEquals(7, subGroups.get(7).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/7", subGroups.get(7).getNodes().get(0).getConfigId());
        assertEquals("node-1-3-50-04", subGroups.get(7).getNodes().get(0).getHostName());
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

        int numberOfHosts = 11;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, deployStateWithClusterEndpoints("foo"), "node-1-3-50-09");
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        // Check slobroks clusters
        assertEquals(1+3, model.getAdmin().getSlobroks().size(), "Includes retired node");
        assertEquals("node-1-3-50-11", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("node-1-3-50-10", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("node-1-3-50-08", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("node-1-3-50-09", model.getAdmin().getSlobroks().get(3).getHostName(), "Included in addition because it is retired");
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

        int numberOfHosts = 12;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, deployStateWithClusterEndpoints("foo"), "node-1-3-50-03", "node-1-3-50-04");
        assertEquals(10+2, model.getRoot().hostSystem().getHosts().size());

        // Check slobroks clusters
        assertEquals(3+2, model.getAdmin().getSlobroks().size(), "Includes retired node");
        assertEquals("node-1-3-50-12", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("node-1-3-50-11", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("node-1-3-50-10", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("node-1-3-50-04", model.getAdmin().getSlobroks().get(3).getHostName(), "Included in addition because it is retired");
        assertEquals("node-1-3-50-03", model.getAdmin().getSlobroks().get(4).getHostName(), "Included in addition because it is retired");
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

        int numberOfHosts = 16;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, deployStateWithClusterEndpoints("foo", "bar"), "node-1-3-50-15", "node-1-3-50-05", "node-1-3-50-04");
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        // Check slobroks clusters
        // ... from cluster default
        assertEquals(7, model.getAdmin().getSlobroks().size(), "Includes retired node");
        assertEquals("node-1-3-50-16", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("node-1-3-50-14", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("node-1-3-50-15", model.getAdmin().getSlobroks().get(2).getHostName(), "Included in addition because it is retired");
        // ... from cluster bar
        assertEquals("node-1-3-50-03", model.getAdmin().getSlobroks().get(3).getHostName());
        assertEquals("node-1-3-50-05", model.getAdmin().getSlobroks().get(5).getHostName(), "Included in addition because it is retired");
        assertEquals("node-1-3-50-04", model.getAdmin().getSlobroks().get(6).getHostName(), "Included in addition because it is retired");
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
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo.indexing", "bar.indexing"));
        assertEquals(7, model.getRoot().hostSystem().getHosts().size());

        // Check cluster controllers
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
    public void testLogForwarderNotInAdminCluster() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'>" +
                        "    <logservers>" +
                        "      <nodes count='1' dedicated='true'/>" +
                        "    </logservers>" +
                        "    <logforwarding>" +
                        "      <splunk deployment-server='bardeplserv:123' client-name='barclinam' phone-home-interval='987' />" +
                        "    </logforwarding>" +
                        "  </admin>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='1'/>" +
                        "  </container>" +
                        "</services>";

        int numberOfHosts = 2;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts+1);

        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        Admin admin = model.getAdmin();
        Logserver logserver = admin.getLogserver();
        HostResource hostResource = logserver.getHostResource();

        assertNotNull(hostResource.getService("logserver"));
        assertNull(hostResource.getService("container"));
        assertNull(hostResource.getService("logforwarder"));

        var clist = model.getContainerClusters().get("foo").getContainers();
        assertEquals(1, clist.size());
        hostResource = clist.get(0).getHostResource();
        assertNull(hostResource.getService("logserver"));
        assertNotNull(hostResource.getService("container"));
        assertNotNull(hostResource.getService("logforwarder"));

        var lfs = hostResource.getService("logforwarder");
        String shutdown = lfs.getPreShutdownCommand().orElse("<none>");
        assertTrue(shutdown.startsWith("$ROOT/bin/vespa-logforwarder-start -S -c hosts/"));
    }


    @Test
    public void testLogForwarderInAdminCluster() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>" +
                        "  <admin version='4.0'>" +
                        "    <logservers>" +
                        "      <nodes count='1' dedicated='true'/>" +
                        "    </logservers>" +
                        "    <logforwarding include-admin='true'>" +
                        "      <splunk deployment-server='bardeplserv:123' client-name='barclinam' phone-home-interval='987' />" +
                        "    </logforwarding>" +
                        "  </admin>" +
                        "  <container version='1.0' id='foo'>" +
                        "     <nodes count='1'/>" +
                        "  </container>" +
                        "</services>";

        int numberOfHosts = 2;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts+1);

        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        Admin admin = model.getAdmin();
        Logserver logserver = admin.getLogserver();
        HostResource hostResource = logserver.getHostResource();

        assertNotNull(hostResource.getService("logserver"));
        assertNull(hostResource.getService("container"));
        assertNotNull(hostResource.getService("logforwarder"));

        var clist = model.getContainerClusters().get("foo").getContainers();
        assertEquals(1, clist.size());
        hostResource = clist.get(0).getHostResource();
        assertNull(hostResource.getService("logserver"));
        assertNotNull(hostResource.getService("container"));
        assertNotNull(hostResource.getService("logforwarder"));
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
        VespaModel model = tester.createModel(services, false, deployStateWithClusterEndpoints("bar.indexing"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        List<StorageGroup> subGroups = cluster.getRootGroup().getSubgroups();
        assertEquals(2*3, cluster.getRedundancy().effectiveInitialRedundancy()); // Reduced from 3*3
        assertEquals(2*3, cluster.getRedundancy().effectiveFinalRedundancy()); // Reduced from 3*4
        assertEquals(2*3, cluster.getRedundancy().effectiveReadyCopies()); // Reduced from 3*3
        assertEquals("2|2|*", cluster.getRootGroup().getPartitions().get()); // Reduced from 4|4|*
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(3, subGroups.size());
        assertEquals("0", subGroups.get(0).getIndex());
        assertEquals(2, subGroups.get(0).getNodes().size());
        assertEquals(0, subGroups.get(0).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/0", subGroups.get(0).getNodes().get(0).getConfigId());
        assertEquals(1, subGroups.get(0).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/1", subGroups.get(0).getNodes().get(1).getConfigId());
        assertEquals("1", subGroups.get(1).getIndex());
        assertEquals(2, subGroups.get(1).getNodes().size());
        assertEquals(2, subGroups.get(1).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/2", subGroups.get(1).getNodes().get(0).getConfigId());
        assertEquals(3, subGroups.get(1).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/3", subGroups.get(1).getNodes().get(1).getConfigId());
        assertEquals("2", subGroups.get(2).getIndex());
        assertEquals(2, subGroups.get(2).getNodes().size());
        assertEquals(4, subGroups.get(2).getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/4", subGroups.get(2).getNodes().get(0).getConfigId());
        assertEquals(5, subGroups.get(2).getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/5", subGroups.get(2).getNodes().get(1).getConfigId());
    }

    @Test
    public void testRedundancyWithGroupsTooHighRedundancyAndOneRetiredNode() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" + // Should have been illegal since we only have 1 node per group
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' groups='2'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        try {
            VespaModel model = tester.createModel(services, false, "node-1-3-50-03");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In content cluster 'bar': This cluster specifies redundancy 2, " +
                         "but this cannot be higher than the minimum nodes per group, which is 1", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testRedundancyWithGroupsAndThreeRetiredNodes() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' groups='2'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 5;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(Zone.defaultZone(), services, false, deployStateWithClusterEndpoints("bar.indexing"), "node-1-3-50-05", "node-1-3-50-04", "node-1-3-50-03");
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(2, cluster.getRedundancy().effectiveInitialRedundancy());
        assertEquals(2, cluster.getRedundancy().effectiveFinalRedundancy());
        assertEquals(2, cluster.getRedundancy().effectiveReadyCopies());
        assertEquals("1|*", cluster.getRootGroup().getPartitions().get());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(2, cluster.getRootGroup().getSubgroups().size());
    }

    @Test
    public void testRedundancy2DownscaledToOneNodeButOneRetired() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "  </content>" +
                "</services>";

        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(Zone.defaultZone(), services, false, false, true,
                                              NodeResources.unspecified(), 0, Optional.empty(),
                                              deployStateWithClusterEndpoints("bar.indexing"), "node-1-3-50-03");
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(2, cluster.getStorageCluster().getChildren().size());
        assertEquals(1, cluster.getRedundancy().effectiveInitialRedundancy());
        assertEquals(1, cluster.getRedundancy().effectiveFinalRedundancy());
        assertEquals(1, cluster.getRedundancy().effectiveReadyCopies());
        assertEquals(2, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().size());
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
                        "     <dispatch><num-dispatch-groups>7</num-dispatch-groups></dispatch>" + // TODO: Allowed, but ignored, remove in Vespa 9
                        "  </content>" +
                        "</services>";

        int numberOfHosts = 6;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false, deployStateWithClusterEndpoints("container"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(4, cluster.getRedundancy().effectiveInitialRedundancy());
        assertEquals(4, cluster.getRedundancy().effectiveFinalRedundancy());
        assertEquals(4, cluster.getRedundancy().effectiveReadyCopies());
        assertEquals(4, cluster.getRedundancy().readyCopies());
        assertFalse(cluster.getRootGroup().getPartitions().isPresent());
        assertEquals(4, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().size());
        assertEquals(4, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/0", cluster.getRootGroup().getNodes().get(0).getConfigId());
        assertEquals(1, cluster.getRootGroup().getNodes().get(1).getDistributionKey());
        assertEquals("bar/storage/1", cluster.getRootGroup().getNodes().get(1).getConfigId());
        assertEquals(2, cluster.getRootGroup().getNodes().get(2).getDistributionKey());
        assertEquals("bar/storage/2", cluster.getRootGroup().getNodes().get(2).getConfigId());
        assertEquals(3, cluster.getRootGroup().getNodes().get(3).getDistributionKey());
        assertEquals("bar/storage/3", cluster.getRootGroup().getNodes().get(3).getConfigId());
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
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, false, deployStateWithClusterEndpoints("bar.indexing"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(1, cluster.getRedundancy().effectiveInitialRedundancy()); // Reduced from 3*3
        assertEquals(1, cluster.getRedundancy().effectiveFinalRedundancy()); // Reduced from 3*4
        assertEquals(1, cluster.getRedundancy().effectiveReadyCopies()); // Reduced from 3*3
        assertFalse(cluster.getRootGroup().getPartitions().isPresent()); // 1 group - > flattened -> no distribution
        assertEquals(1, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().size());
        assertEquals(1, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/0", cluster.getRootGroup().getNodes().get(0).getConfigId());
    }

    @Test
    public void testRequiringMoreNodesThanAreAvailable() {
        assertThrows(IllegalArgumentException.class, () -> {
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
        });
    }

    @Test
    public void testRequiredNodesAndDedicatedClusterControllers() {
        assertThrows(IllegalArgumentException.class, () -> {
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
        });
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
        VespaModel model = tester.createModel(services, false, deployStateWithClusterEndpoints("container"));
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
        VespaModel model = tester.createModel(services, false, deployStateWithClusterEndpoints("bar.indexing"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        ContentCluster cluster = model.getContentClusters().get("bar");
        assertEquals(1, cluster.getRedundancy().effectiveInitialRedundancy());
        assertEquals(1, cluster.getRedundancy().effectiveFinalRedundancy());
        assertEquals(1, cluster.getRedundancy().effectiveReadyCopies());

        assertFalse(cluster.getRootGroup().getPartitions().isPresent());
        assertEquals(1, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getSubgroups().size());
        assertEquals(1, cluster.getRootGroup().getNodes().size());
        assertEquals(0, cluster.getRootGroup().getNodes().get(0).getDistributionKey());
        assertEquals("bar/storage/0", cluster.getRootGroup().getNodes().get(0).getConfigId());
    }

    @Test
    public void testRequestingSpecificNodeResources() {
        String services =
                """
                <?xml version='1.0' encoding='utf-8' ?>
                <services>
                   <admin version='4.0'>
                      <logservers>
                         <nodes count='1' dedicated='true'>
                            <resources vcpu='0.1' memory='0.2Gb' disk='300Gb' disk-speed='slow'/>
                         </nodes>
                      </logservers>
                      <slobroks>
                         <nodes count='2' dedicated='true'>
                            <resources vcpu='0.1' memory='0.3Gb' disk='1Gb' bandwidth='500Mbps'/>
                         </nodes>
                      </slobroks>
                   </admin>
                   <container version='1.0' id='container'>
                      <nodes count='4'>
                         <resources vcpu='12' memory='10Gb' disk='30Gb' architecture='arm64'/>
                      </nodes>
                   </container>
                   <container version='1.0' id='container2'>
                      <nodes count='2'>
                         <resources vcpu='4' memory='16Gb' disk='125Gb'>
                           <gpu count='1' memory='16Gb'/>
                         </resources>
                      </nodes>
                   </container>
                   <content version='1.0' id='foo'>
                     <redundancy>3</redundancy>
                      <documents>
                        <document type='type1' mode='index'/>
                      </documents>
                      <nodes count='5'>
                         <resources vcpu='8' memory='200Gb' disk='1Pb'/>
                      </nodes>
                   </content>
                   <content version='1.0' id='bar'>
                     <redundancy>3</redundancy>
                      <documents>
                        <document type='type1' mode='index'/>
                      </documents>
                      <nodes count='6'>
                         <resources vcpu='10' memory='64Gb' disk='200Gb'/>
                      </nodes>
                   </content>
                </services>
                """;

        int totalHosts = 23;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(new NodeResources(0.1, 0.2, 300, 0.3, NodeResources.DiskSpeed.slow), 1);// Logserver
        tester.addHosts(new NodeResources(0.1, 0.3, 1, 0.5), 2); // Slobrok
        tester.addHosts(new NodeResources(12, 10, 30, 0.3,
                                          NodeResources.DiskSpeed.fast, NodeResources.StorageType.local, NodeResources.Architecture.arm64), 4); // Container
        tester.addHosts(new NodeResources(4, 16, 125, 10,
                                          NodeResources.DiskSpeed.fast, NodeResources.StorageType.local, Architecture.x86_64,
                                          new NodeResources.GpuResources(1, 16)), 4); // Container 2
        tester.addHosts(new NodeResources(8, 200, 1000000, 0.3), 5); // Content-foo
        tester.addHosts(new NodeResources(10, 64, 200, 0.3), 6); // Content-bar
        tester.addHosts(new NodeResources(0.5, 2, 10, 0.3), 6); // Cluster-controller
        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, false, false,
                                              NodeResources.unspecified(), 0, Optional.empty(),
                                              deployStateWithClusterEndpoints("container", "container2"));
        assertEquals(totalHosts, model.getRoot().hostSystem().getHosts().size());
    }

    @Test
    public void testRequestingRangesMin() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "   <container version='1.0' id='default'>" +
                "      <nodes count='[4, 6]'>" +
                "         <resources vcpu='[11.5, 13.5]' memory='[10Gb, 100Gb]' disk='[30Gb, 1Tb]'/>" +
                "      </nodes>" +
                "   </container>" +
                "   <content version='1.0' id='foo'>" +
                "      <redundancy>2</redundancy>" +
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
        tester.addHosts(new NodeResources(11.5,  10,           30, 0.3), 6);
        tester.addHosts(new NodeResources(85,   200, 1000_000_000, 0.3), 20);
        tester.addHosts(new NodeResources( 0.5,   2,           10, 0.3), 3);
        VespaModel model = tester.createModel(services, true);
        assertEquals(4 + 6 + 1, model.getRoot().hostSystem().getHosts().size());
    }

    @Test
    public void testRequestingRangesMax() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "   <container version='1.0' id='default'>" +
                "      <nodes count='[4, 6]'>" +
                "         <resources vcpu='[11.5, 13.5]' memory='[10Gb, 100Gb]' disk='[30Gb, 1Tb]'/>" +
                "      </nodes>" +
                "   </container>" +
                "   <content version='1.0' id='foo'>" +
                "      <redundancy>2</redundancy>" +
                "      <documents>" +
                "        <document type='type1' mode='index'/>" +
                "      </documents>" +
                "      <nodes count='[6, 20]' groups='[3,4]'>" +
                "         <resources vcpu='8' memory='200Gb' disk='1Pb'/>" +
                "      </nodes>" +
                "   </content>" +
                "</services>";

        int totalHosts = 29;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(new NodeResources(13.5, 100,         1000, 0.3), 6);
        tester.addHosts(new NodeResources(85,   200, 1000_000_000, 0.3), 20);
        tester.addHosts(new NodeResources( 0.5,   2,           10, 0.3), 3);
        VespaModel model = tester.createModel(services, true, true);
        assertEquals(totalHosts, model.getRoot().hostSystem().getHosts().size());
    }

    @Test
    public void testUseArm64NodesForAdminCluster() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "   <admin version='4.0'>" +
                        "   </admin>" +
                        "   <container version='1.0' id='default'>" +
                        "      <nodes count='2'>" +
                        "         <resources vcpu='2' memory='8Gb' disk='30Gb'/>" +
                        "      </nodes>" +
                        "   </container>" +
                        "   <content version='1.0' id='foo'>" +
                        "      <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='type1' mode='index'/>" +
                        "      </documents>" +
                        "      <nodes count='2'>" +
                        "         <resources vcpu='2' memory='8Gb' disk='30Gb'/>" +
                        "      </nodes>" +
                        "   </content>" +
                        "</services>";

        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(true);
        tester.useDedicatedNodeForLogserver(true);
        tester.addHosts(new NodeResources(13.5, 100, 1000, 0.3), 4);
        tester.addHosts(new NodeResources(0.5, 2, 50, 0.3, DiskSpeed.fast, StorageType.any, Architecture.arm64), 4); // 3 ccs, 1 logserver
        VespaModel model = tester.createModel(services, true, true);
        List<HostResource> hosts = model.getRoot().hostSystem().getHosts();
        assertEquals(8, hosts.size());

        Set<HostResource> clusterControllerResources = getHostResourcesForService(hosts, "container-clustercontroller");
        assertEquals(3, clusterControllerResources.size());
        assertTrue(clusterControllerResources.stream().allMatch(host -> host.realResources().architecture() == Architecture.arm64));

        Set<HostResource> logserverResources = getHostResourcesForService(hosts, "logserver-container");
        assertEquals(1, logserverResources.size());
        assertTrue(logserverResources.stream().allMatch(host -> host.realResources().architecture() == Architecture.arm64));

        // Other hosts should be default
        assertTrue(hosts.stream()
                        .filter(host -> !clusterControllerResources.contains(host))
                        .filter(host -> !logserverResources.contains(host))
                        .allMatch(host -> host.realResources().architecture() == Architecture.getDefault()));
    }

    private Set<HostResource> getHostResourcesForService(List<HostResource> hosts, String service) {
        return hosts.stream()
                    .filter(host -> host.getHostInfo().getServices().stream()
                                        .anyMatch(s -> s.getServiceType().equals(service)))
                    .collect(Collectors.toSet());
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
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("container"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());
        assertEquals(3, model.getContainerClusters().get("container").getContainers().size());
        assertNotNull(model.getAdmin().getLogserver());
        assertEquals(3, model.getAdmin().getSlobroks().size());
    }

    @Test
    public void testJvmOptions() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<container version='1.0'>" +
                        "  <search/>" +
                        "  <nodes count='3'>" +
                        "    <jvm options='-DfooOption=xyz' /> " +
                        "  </nodes>" +
                        "</container>";
        int numberOfHosts = 3;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("container"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());
        assertEquals("-DfooOption=xyz", model.getContainerClusters().get("container").getContainers().get(0).getAssignedJvmOptions());
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
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
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
        VespaModel model = tester.createModel(new Zone(Environment.dev, RegionName.from("us-central-1")), services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(3, model.getHosts().size(), "We get 1 node per cluster and no admin node apart from the dedicated cluster controller");
        assertEquals(1, model.getContainerClusters().size());
        assertEquals(1, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().countNodes(true));
        assertEquals(1, model.getAdmin().getClusterControllers().getContainers().size());
    }

    @Test
    public void testThatStandaloneSyntaxWithClusterControllerWorksOnHostedManuallyDeployed() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "   <container id='foo' version='1.0'>" +
                        "      <nodes count=\"2\" />" +
                        "   </container>" +
                        "   <content id='bar' version='1.0'>" +
                        "      <documents>" +
                        "         <document type='type1' mode='index'/>" +
                        "      </documents>" +
                        "      <redundancy>1</redundancy>" +
                        "      <nodes>" +
                        "          <group>" +
                        "            <node distribution-key='0' hostalias='node3'/>" +
                        "          </group>" +
                        "      </nodes>" +
                        "   </content>" +
                        "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(true);
        tester.addHosts(4);
        try {
            VespaModel model = tester.createModel(new Zone(Environment.staging, RegionName.from("us-central-1")), services, true);
            fail("expected failure");
        } catch (IllegalArgumentException e) {
            assertEquals("In content cluster 'bar': Clusters in hosted environments must have a <nodes count='N'> tag\n" +
                         "matching all zones, and having no <node> subtags,\nsee https://cloud.vespa.ai/en/reference/services",
                         Exceptions.toMessageString(e));
        }
    }

    /** Deploying an application with "nodes count" standalone should give a single-node deployment */
    @Test
    public void testThatHostedSyntaxWorksOnStandalone() {
        String services =
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
        VespaModel model = tester.createModel(services, true);

        assertEquals(1,
                     model.getContainerClusters().get("container1").getContainers().size(),
                     "Nodes in container cluster");
        assertEquals(1,
                     model.getContentClusters().get("content").getRootGroup().getNodes().size(),
                     "Nodes in content cluster (downscaled)");

        assertEquals(1, model.getAdmin().getSlobroks().size());

        model.getConfig(new StorStatusConfig.Builder(), "default");
        StorageCluster storage = model.getContentClusters().get("content").getStorageCluster();
        StorCommunicationmanagerConfig.Builder builder = new StorCommunicationmanagerConfig.Builder();
        storage.getChildren().get("0").getConfig(builder);
    }

    @Test
    public void testMinRedundancyMetByGroups() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <content version='1.0'>" +
                "     <min-redundancy>2</min-redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' groups='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(true);
        tester.addHosts(6);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("container1"));

        var contentCluster = model.getContentClusters().get("content");
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        contentCluster.getSearch().getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        assertEquals(1, protonConfig.distribution().searchablecopies());
        assertEquals(1, protonConfig.distribution().redundancy());
    }

    @Test
    public void testMinRedundancyAndSearchableCopies() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <content version='1.0'>" +
                "     <min-redundancy>2</min-redundancy>" +
                "     <engine><proton><searchable-copies>1</searchable-copies></proton></engine>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(true);
        tester.addHosts(6);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("container1"));

        var contentCluster = model.getContentClusters().get("content");
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        contentCluster.getSearch().getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        assertEquals(1, protonConfig.distribution().searchablecopies());
        assertEquals(2, protonConfig.distribution().redundancy());
    }

    @Test
    public void testMinRedundancyMetWithinGroup() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <content version='1.0'>" +
                "     <min-redundancy>2</min-redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' groups='1'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(true);
        tester.addHosts(6);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("container1"));

        var contentCluster = model.getContentClusters().get("content");
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        contentCluster.getSearch().getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        assertEquals(2, protonConfig.distribution().searchablecopies());
        assertEquals(2, protonConfig.distribution().redundancy());
    }

    @Test
    public void testRedundancy1() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='container1'>" +
                "     <nodes count='1'/>" +
                "  </container>" +
                "  <content version='1.0'>" +
                "     <min-redundancy>1</min-redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "     <nodes count='2' groups='1'/>" +
                "   </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(true);
        tester.addHosts(6);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("container1"));

        var contentCluster = model.getContentClusters().get("content");
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        contentCluster.getSearch().getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        assertEquals(1, protonConfig.distribution().searchablecopies());
        assertEquals(1, protonConfig.distribution().redundancy());
    }

    /**
     * Deploying an application with "nodes count" standalone should give a single-node deployment,
     * also if the user has a lingering hosts file from running self-hosted.
     *
     * NOTE: This does *not* work (but gives an understandable error message),
     *       but the current code does not get provoke the error that is thrown from HostsXmlProvisioner.prepare
     */
    @Test
    public void testThatHostedSyntaxWorksOnStandaloneAlsoWithAHostedFile() {
        String services =
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

        VespaModelTester tester = new VespaModelTester();
        tester.setHosted(false);
        tester.addHosts(3);
        VespaModel model = tester.createModel(services, hosts, true);

        assertEquals(1,
                     model.getContainerClusters().get("container1").getContainers().size(),
                     "Nodes in container cluster");
        assertEquals(1,
                     model.getContentClusters().get("content").getRootGroup().getNodes().size(),
                     "Nodes in content cluster (downscaled)");

        assertEquals(1, model.getAdmin().getSlobroks().size());

        model.getConfig(new StorStatusConfig.Builder(), "default");
        StorageCluster storage = model.getContentClusters().get("content").getStorageCluster();
        StorCommunicationmanagerConfig.Builder builder = new StorCommunicationmanagerConfig.Builder();
        storage.getChildren().get("0").getConfig(builder);
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
                "     <redundancy>3</redundancy>" +
                "     <documents>" +
                "       <document type='type1' mode='index'/>" +
                "     </documents>" +
                "  </content>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(6);
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(6, model.getRoot().hostSystem().getHosts().size());
        assertEquals(3, model.getAdmin().getSlobroks().size());
        assertEquals(2, model.getContainerClusters().get("foo").getContainers().size());
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().countNodes(true));
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
        VespaModel model = tester.createModel(services, true, deployStateWithClusterEndpoints("foo"));
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
                "     <redundancy>3</redundancy>" +
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
                "     <redundancy>3</redundancy>" +
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
        assertEquals(1, model.getContentClusters().get("bar").getRootGroup().countNodes(true));
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
        ContainerCluster<?> controller = model.getAdmin().getClusterControllers();
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
                "    <nodes>\n" +
                "      <jvm options=\"-Xms512m -Xmx512m\"/>\n" +
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
        assertEquals(1, model.getRoot().hostSystem().getHosts().size());
        ContentCluster content = model.getContentClusters().get("storage");
        assertEquals(1, content.getRootGroup().getNodes().size());
        ContainerCluster<?> controller = model.getAdmin().getClusterControllers();
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
        assertEquals(1, model.getRoot().hostSystem().getHosts().size());
        ContentCluster content = model.getContentClusters().get("storage");
        assertEquals(2, content.getRootGroup().getNodes().size());
        ContainerCluster<?> controller = model.getAdmin().getClusterControllers();
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
        tester.addHosts(9);
        VespaModel model = tester.createModel(servicesXml, true, deployStateWithClusterEndpoints("qrs", "zk"));

        Map<String, Boolean> tests = Map.of("qrs", false,
                                            "zk", true,
                                            "content", true);
        Map<String, List<HostResource>> hostsByCluster = model.hostSystem().getHosts().stream()
                                                              .collect(Collectors.groupingBy(h -> h.spec().membership().get().cluster().id().value()));
        tests.forEach((clusterId, stateful) -> {
            List<HostResource> hosts = hostsByCluster.getOrDefault(clusterId, List.of());
            assertFalse(hosts.isEmpty(), "Hosts are provisioned for '" + clusterId + "'");
            assertEquals(stateful,
                         hosts.stream().allMatch(h -> h.spec().membership().get().cluster().isStateful()),
                         "Hosts in cluster '" + clusterId + "' are " + (stateful ? "" : "not ") + "stateful");
        });
    }

    @Test
    public void testAllow2ContentGroupsDown() {
        String servicesXml =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                        "<services>" +
                        "  <container version='1.0' id='qrs'>" +
                        "     <nodes count='1'/>" +
                        "  </container>" +
                        "  <content version='1.0' id='content'>" +
                        "     <redundancy>1</redundancy>" +
                        "     <documents>" +
                        "       <document type='type1' mode='index'/>" +
                        "     </documents>" +
                        "    <nodes count='4' groups='4'/>" +
                        "    <tuning>" +
                        "      <cluster-controller>" +
                        "        <groups-allowed-down-ratio>0.5</groups-allowed-down-ratio>" +
                        "      </cluster-controller>" +
                        "    </tuning>" +
                        "  </content>" +
                        "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(9);
        VespaModel model = tester.createModel(servicesXml, true, deployStateWithClusterEndpoints("qrs").properties(new TestProperties()));

        var fleetControllerConfigBuilder = new FleetcontrollerConfig.Builder();
        model.getConfig(fleetControllerConfigBuilder, "admin/standalone/cluster-controllers/0/components/clustercontroller-content-configurer");
        assertEquals(2, fleetControllerConfigBuilder.build().max_number_of_groups_allowed_to_be_down());
    }

    @Test
    public void containerWithZooKeeperSuboptimalNodeCountDuringRetirement() {
        String servicesXml =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" +
                "  <container version='1.0' id='zk'>" +
                "     <zookeeper/>" +
                "     <nodes count='3'/>" +
                "  </container>" +
                "</services>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(4);
        VespaModel model = tester.createModel(Zone.defaultZone(), servicesXml, true, deployStateWithClusterEndpoints("zk"), "node-1-3-50-04");
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
        VespaModel model = tester.createModel(servicesXml.apply(3), true, deployStateWithClusterEndpoints("zk"));

        {
            ApplicationContainerCluster cluster = model.getContainerClusters().get("zk");
            ZookeeperServerConfig.Builder config = new ZookeeperServerConfig.Builder();
            cluster.getContainers().forEach(c -> c.getConfig(config));
            cluster.getConfig(config);
            assertTrue(config.build().server().stream().noneMatch(ZookeeperServerConfig.Server::joining), "Initial servers are not joining");
        }
        {
            VespaModel nextModel = tester.createModel(Zone.defaultZone(), servicesXml.apply(3), true, false, false, NodeResources.unspecified(), 0, Optional.of(model), deployStateWithClusterEndpoints("zk"), "node-1-3-50-04", "node-1-3-50-03");
            ApplicationContainerCluster cluster = nextModel.getContainerClusters().get("zk");
            ZookeeperServerConfig.Builder config = new ZookeeperServerConfig.Builder();
            cluster.getContainers().forEach(c -> c.getConfig(config));
            cluster.getConfig(config);
            assertEquals(Map.of(0, false,
                                1, false,
                                2, false,
                                3, true,
                                4, true),
                         config.build().server().stream().collect(Collectors.toMap(ZookeeperServerConfig.Server::id,
                                                                                   ZookeeperServerConfig.Server::joining)),
                         "New nodes are joining");
            assertEquals(Map.of(0, false,
                                1, true,
                                2, true,
                                3, false,
                                4, false),
                         config.build().server().stream().collect(Collectors.toMap(ZookeeperServerConfig.Server::id,
                                                                                   ZookeeperServerConfig.Server::retired)),
                         "Retired nodes are retired");
        }
    }

    private VespaModel createNonProvisionedMultitenantModel(String services) {
        return createNonProvisionedModel(true, null, services);
    }

    private VespaModel createNonProvisionedModel(boolean multitenant, String hosts, String services) {
        VespaModelCreatorWithMockPkg modelCreatorWithMockPkg = new VespaModelCreatorWithMockPkg(hosts, services, generateSchemas("type1"));
        ApplicationPackage appPkg = modelCreatorWithMockPkg.appPkg;
        DeployState deployState = new DeployState.Builder().applicationPackage(appPkg).
                properties((new TestProperties()).setMultitenant(multitenant)).
                build();
        return modelCreatorWithMockPkg.create(false, deployState);
    }

    private int physicalMemoryPercentage(ContainerCluster<?> cluster) {
        QrStartConfig.Builder b = new QrStartConfig.Builder();
        cluster.getConfig(b);
        return b.build().jvm().heapSizeAsPercentageOfPhysicalMemory();
    }

    private long protonMemorySize(ContentCluster cluster) {
        ProtonConfig.Builder b  = new ProtonConfig.Builder();
        cluster.getSearch().getSearchCluster().getSearchNode(0).getConfig(b);
        return b.build().hwinfo().memory().size();
    }

    @Test
    public void require_that_proton_config_is_tuned_based_on_node_resources() {
         String services = joinLines("<?xml version='1.0' encoding='utf-8' ?>",
                 "<services>",
                 "  <content version='1.0' id='test'>",
                 "     <redundancy>2</redundancy>" +
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
         VespaModel model = tester.createModel(Zone.defaultZone(), services, true, false, false, NodeResources.unspecified(), 0, Optional.empty(), deployStateWithClusterEndpoints("test.indexing"));

         ContentSearchCluster cluster = model.getContentClusters().get("test").getSearch();
         assertEquals(2, cluster.getSearchNodes().size());
         assertEquals(40, getProtonConfig(cluster, 0).hwinfo().disk().writespeed(), 0.001);
         assertEquals(40, getProtonConfig(cluster, 1).hwinfo().disk().writespeed(), 0.001);
    }

    @Test
    public void require_that_resources_can_be_partially_specified() {
        String services = joinLines("<?xml version='1.0' encoding='utf-8' ?>",
                                    "<services>",
                                    "  <content version='1.0' id='test'>",
                                    "     <redundancy>2</redundancy>" +
                                    "     <documents>",
                                    "       <document type='type1' mode='index'/>",
                                    "     </documents>",
                                    "     <nodes count='2'>",
                                    "       <resources vcpu='1'/>",
                                    "     </nodes>",
                                    "  </content>",
                                    "</services>");

        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(new NodeResources(1, 3, 10, 5), 5);
        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, false, false, new NodeResources(1.0, 3.0, 9.0, 1.0), 0, Optional.empty(), deployStateWithClusterEndpoints("test.indexing"));
        ContentSearchCluster cluster = model.getContentClusters().get("test").getSearch();
        assertEquals(2, cluster.getSearchNodes().size());
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
                "    <redundancy>1</redundancy>" +
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
        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, false, false, NodeResources.unspecified(), 0, Optional.empty(), deployStateWithClusterEndpoints("test.indexing"));
        ContentSearchCluster cluster = model.getContentClusters().get("test").getSearch();
        ProtonConfig cfg = getProtonConfig(model, cluster.getSearchNodes().get(0).getConfigId());
        assertEquals(2000, cfg.flush().memory().maxtlssize()); // from config override
        assertEquals(1000, cfg.flush().memory().maxmemory()); // from explicit tuning
        assertEquals((long) ((128 - memoryOverheadGb) * GiB * 0.08), cfg.flush().memory().each().maxmemory()); // from default node flavor tuning
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

        VespaModel model = tester.createModel(Zone.defaultZone(), services, true, deployStateWithClusterEndpoints("foo"));
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

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
        assertEquals(nodeCount,
                     model.hostSystem().getHosts().stream()
                          .map(h -> h.spec().membership().get().cluster())
                          .filter(spec -> spec.id().equals(id) && spec.type().equals(type) && spec.combinedId().equals(Optional.ofNullable(combinedId)))
                          .count(),
                     "Nodes in cluster " + id + " with type " + type + (combinedId != null ? ", combinedId " + combinedId : ""));
    }

    private static void assertProvisioned(int nodeCount, ClusterSpec.Id id, ClusterSpec.Type type, VespaModel model) {
        assertProvisioned(nodeCount, id, null, type, model);
    }

    private static boolean hostNameExists(HostSystem hostSystem, String hostname) {
        return hostSystem.getHosts().stream().map(HostResource::getHost).anyMatch(host -> host.getHostname().equals(hostname));
    }

    private static DeployState.Builder deployStateWithClusterEndpoints(String... cluster) {
        Set<ContainerEndpoint> endpoints = Arrays.stream(cluster)
                                                 .map(c -> new ContainerEndpoint(c,
                                                                                 ApplicationClusterEndpoint.Scope.zone,
                                                                                 List.of(c + ".example.com")))
                                                 .collect(Collectors.toSet());
        return new DeployState.Builder().endpoints(endpoints);
    }

    record TestLogger(List<LogMessage> msgs) implements DeployLogger {

        public TestLogger() {
            this(new ArrayList<>());
        }

        @Override
        public void log(Level level, String message) {
            msgs.add(new LogMessage(level, message));
        }

        record LogMessage(Level level, String message) {}

    }

}
