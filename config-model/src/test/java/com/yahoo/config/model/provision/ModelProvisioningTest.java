// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Slobrok;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.search.Dispatch;
import com.yahoo.vespa.model.test.VespaModelTester;
import org.junit.Test;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;

/**
 * Test cases for provisioning nodes to entire vespamodels
 *
 * @author vegardh
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
                        "  <handler id='myHandler'>" +
                        "    <component id='injected' />" +
                        "  </handler>" +
                        "  <nodes count='2' jvmargs='-verbosegc' preload='lib/blablamalloc.so'/>" +
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
        VespaModel model = creator.create(new DeployState.Builder().modelHostProvisioner(new InMemoryProvisioner(Hosts.getHosts(new StringReader(hosts)), true)));
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
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(0).getPreLoad(), is(Defaults.getDefaults().vespaHome() + "lib64/vespa/malloc/libvespamalloc.so"));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(1).getPreLoad(), is(Defaults.getDefaults().vespaHome() + "lib64/vespa/malloc/libvespamalloc.so"));
        assertThat(model.getContainerClusters().get("mydisc").getContainers().get(2).getPreLoad(), is(Defaults.getDefaults().vespaHome() + "lib64/vespa/malloc/libvespamalloc.so"));

        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(0).getJvmArgs(), is("-verbosegc"));
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(1).getJvmArgs(), is("-verbosegc"));
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(0).getPreLoad(), is("lib/blablamalloc.so"));
        assertThat(model.getContainerClusters().get("mydisc2").getContainers().get(1).getPreLoad(), is("lib/blablamalloc.so"));

        final HostSystem hostSystem = model.getHostSystem();
        assertNotNull(hostSystem.getHostByHostname("myhost0"));
        assertNotNull(hostSystem.getHostByHostname("myhost1"));
        assertNotNull(hostSystem.getHostByHostname("myhost2"));
        assertNotNull(hostSystem.getHostByHostname("myhost3"));
        assertNull(hostSystem.getHostByHostname("Nope"));
    }

    @Test
    public void testNodeCountForContentGroup() throws Exception {
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
    public void testNodeCountForContentGroupHierarchy() throws ParseException {
        String services = 
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>\n" +
                        "\n" +
                        "  <admin version='3.0'>\n" +
                        "    <nodes count='3'/>" + // Ignored
                        "  </admin>\n" +
                        "  <content version='1.0' id='bar'>" +
                        "    <redundancy>2</redundancy>\n" +
                        "    <documents>" +
                        "      <document type='type1' mode='index'/>" +
                        "    </documents>" +
                        "    <group>" +
                        "      <distribution partitions=\"1|*\"/>" +
                        "      <group name='0' distribution-key='0'>" +
                        "        <nodes count='2'/> " +
                        "      </group>" +
                        "      <group name='1' distribution-key='1'>" +
                        "        <nodes count='2'/> " +
                        "      </group>" +
                        "    </group>" +
                        "  </content>" +
                        "  <content version='1.0' id='baz'>" +
                        "    <redundancy>2</redundancy>\n" +
                        "    <documents>" +
                        "      <document type='type1' mode='index'/>" +
                        "    </documents>" +
                        "    <group>" +
                        "      <distribution partitions=\"1|*\"/>" +
                        "      <group name='0' distribution-key='10'>" +
                        "        <nodes count='1'/> " +
                        "      </group>" +
                        "      <group name='1' distribution-key='11'>" +
                        "        <nodes count='1'/> " +
                        "      </group>" +
                        "    </group>" +
                        "  </content>" +
                        "\n" +
                        "</services>";
        
        int numberOfHosts = 6;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));
        
        ContentCluster cluster = model.getContentClusters().get("bar");
        assertThat(cluster.getRootGroup().getNodes().size(), is(0));
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

        cluster = model.getContentClusters().get("baz");
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("10"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("baz/storage/0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("11"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("baz/storage/1"));
    }

    @Test
    public void testUsingNodesAndGroupCountAttributes() throws ParseException {
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
        assertEquals("foo10", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("foo13", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("foo16", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(9, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertEquals("foo10", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getConfigId(), is("bar/storage/1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getDistributionKey(), is(2));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(2).getConfigId(), is("bar/storage/2"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(3));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/3"));
        assertEquals("foo13", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getDistributionKey(), is(4));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(1).getConfigId(), is("bar/storage/4"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getDistributionKey(), is(5));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(2).getConfigId(), is("bar/storage/5"));
        // ...
        assertEquals("foo16", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
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
        assertEquals("foo37", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("foo38", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("foo39", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(27, cluster.getRootGroup().getSubgroups().size());
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("baz/storage/0"));
        assertEquals("foo37", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("baz/storage/1"));
        assertEquals("foo38", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        // ...
        assertEquals("foo39", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
        // ...
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getIndex(), is("26"));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().get(0).getDistributionKey(), is(26));
        assertThat(cluster.getRootGroup().getSubgroups().get(26).getNodes().get(0).getConfigId(), is("baz/storage/26"));
    }

    @Test
    public void testGroupsOfSize1() throws ParseException {
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
        assertEquals("foo10", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("foo11", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("foo12", clusterControllers.getContainers().get(2).getHostName());
        assertEquals(0, cluster.getRootGroup().getNodes().size());
        assertEquals(8, cluster.getRootGroup().getSubgroups().size());
        assertEquals(8, cluster.distributionBits());
        // first group
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getIndex(), is("0"));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getDistributionKey(), is(0));
        assertThat(cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getConfigId(), is("bar/storage/0"));
        assertEquals("foo10", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        // second group
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getIndex(), is("1"));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getDistributionKey(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getConfigId(), is("bar/storage/1"));
        assertEquals("foo11", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        // ... last group
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getIndex(), is("7"));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().size(), is(1));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getDistributionKey(), is(7));
        assertThat(cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getConfigId(), is("bar/storage/7"));
        assertEquals("foo17", cluster.getRootGroup().getSubgroups().get(7).getNodes().get(0).getHostName());
    }

    @Test
    public void testExplicitNonDedicatedClusterControllers() throws ParseException {
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
        assertEquals("We get the closest odd numer", 5, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("foo10", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("foo11", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("foo13", clusterControllers.getContainers().get(2).getHostName());
        assertEquals("foo14", clusterControllers.getContainers().get(3).getHostName()); // Should be 16 for perfect distribution ...
        assertEquals("foo10", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(0).getHostName());
        assertEquals("foo11", cluster.getRootGroup().getSubgroups().get(0).getNodes().get(1).getHostName());
        assertEquals("foo13", cluster.getRootGroup().getSubgroups().get(1).getNodes().get(0).getHostName());
        assertEquals("foo16", cluster.getRootGroup().getSubgroups().get(2).getNodes().get(0).getHostName());
    }

    @Test
    public void testClusterControllersAreNotPlacedOnRetiredNodes() throws ParseException {
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
        VespaModel model = tester.createModel(services, true, "foo10", "foo13", "foo16");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check content clusters
        ContentCluster cluster = model.getContentClusters().get("bar");
        ContainerCluster clusterControllers = cluster.getClusterControllers();
        assertEquals(3, clusterControllers.getContainers().size());
        assertEquals("bar-controllers", clusterControllers.getName());
        assertEquals("Skipping retired foo10", "foo11", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("Skipping retired foo13", "foo14", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("Skipping retired foo16", "foo17", clusterControllers.getContainers().get(2).getHostName());
    }

    @Test
    public void testSlobroksClustersAreExpandedToIncludeRetiredNodes() throws ParseException {
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
        VespaModel model = tester.createModel(services, true, "foo0");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        assertEquals("Includes retired node", 1+3, model.getAdmin().getSlobroks().size());
        assertEquals("foo1", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("foo2", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("foo3", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("Included in addition because it is retired", "foo0", model.getAdmin().getSlobroks().get(3).getHostName());
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
        VespaModel model = tester.createModel(services, true, "foo3", "foo4");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        assertEquals("Includes retired node", 3+2, model.getAdmin().getSlobroks().size());
        assertEquals("foo0", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("foo1", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("foo2", model.getAdmin().getSlobroks().get(2).getHostName());
        assertEquals("Included in addition because it is retired", "foo3", model.getAdmin().getSlobroks().get(3).getHostName());
        assertEquals("Included in addition because it is retired", "foo4", model.getAdmin().getSlobroks().get(4).getHostName());
    }

    @Test
    public void testSlobroksAreSpreadOverAllContainerClusters() throws ParseException {
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
        VespaModel model = tester.createModel(services, true, "foo0", "foo10", "foo11");
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        // Check slobroks clusters
        // ... from cluster foo
        assertEquals("Includes retired node", 3+3, model.getAdmin().getSlobroks().size());
        assertEquals("foo1", model.getAdmin().getSlobroks().get(0).getHostName());
        assertEquals("foo2", model.getAdmin().getSlobroks().get(1).getHostName());
        assertEquals("Included in addition because it is retired", "foo0", model.getAdmin().getSlobroks().get(2).getHostName());
        // ... from cluster bar
        assertEquals("foo12", model.getAdmin().getSlobroks().get(3).getHostName());
        assertEquals("Included in addition because it is retired", "foo10", model.getAdmin().getSlobroks().get(4).getHostName());
        assertEquals("Included in addition because it is retired", "foo11", model.getAdmin().getSlobroks().get(5).getHostName());
    }

    @Test
    public void test2ContentNodesProduces1ClusterController() throws ParseException {
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
    public void testExplicitDedicatedClusterControllers() throws ParseException {
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
        assertEquals("foo19", clusterControllers.getContainers().get(0).getHostName());
        assertEquals("foo20", clusterControllers.getContainers().get(1).getHostName());
        assertEquals("foo21", clusterControllers.getContainers().get(2).getHostName());
        assertEquals("foo22", clusterControllers.getContainers().get(3).getHostName());
    }

    @Test
    public void testUsingNodesAndGroupCountAttributesAndGettingTooFewNodes() throws ParseException {
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
    public void testUsingNodesCountAttributesAndGettingTooFewNodes() throws ParseException {
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
        assertEquals("foo0", clusterControllers.getContainers().get(0).getHostName());
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

    @Test
    public void testUsingNodesCountAttributesAndGettingJustOneNode() throws ParseException {
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
    public void testRequestingSpecificFlavors() throws ParseException {
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
    public void testJDiscOnly() throws Exception {
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
    public void testUsingHostaliasWithProvisioner() throws Exception {
        String services =
                        "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services>\n" +
                        "\n" +
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
        int numberOfHosts = 1;
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        VespaModel model = tester.createModel(services, true);
        assertEquals(1, model.getRoot().getHostSystem().getHosts().size());
        assertEquals(1, model.getAdmin().getSlobroks().size());
    }

    @Test
    public void testThatStandaloneSyntaxWorksOnHostedVespa() throws ParseException {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<jdisc id='foo' version='1.0'>" +
                "  <http>" +
                "    <server id='server1' port='" + Defaults.getDefaults().vespaWebServicePort() + "' />" +
                "  </http>" +
                "</jdisc>";
        VespaModelTester tester = new VespaModelTester();
        tester.addHosts(1);
        VespaModel model = tester.createModel(services, true);
        assertThat(model.getHosts().size(), is(1));
        assertThat(model.getContainerClusters().size(), is(1));
    }

    /** Recreate the combination used in some factory tests */
    @Test
    public void testMultitenantButNotHosted() throws Exception {
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
    public void testMultitenantButNotHostedSharedContentNode() throws Exception {
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

    private VespaModel createNonProvisionedMultitenantModel(String services) throws ParseException {
        final VespaModelCreatorWithMockPkg modelCreatorWithMockPkg = new VespaModelCreatorWithMockPkg(null, services, ApplicationPackageUtils.generateSearchDefinition("type1"));
        final ApplicationPackage appPkg = modelCreatorWithMockPkg.appPkg;
        DeployState deployState = new DeployState.Builder().applicationPackage(appPkg).
                properties((new DeployProperties.Builder()).multitenant(true).build()).
                build();
        return modelCreatorWithMockPkg.create(false, deployState);
    }

    @Test
    public void testThatTldConfigIdsAreDeterministic() throws ParseException {
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
            // Nodes used will be foo0, foo1, .. and so on.
            VespaModel model = tester.createModel(services, true);
            assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

            final Map<String, ContentCluster> contentClusters = model.getContentClusters();
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
            // Nodes used will be foo1, foo2, .. and so on. Containers will start with index 1, not 0 as they are in the test above
            VespaModel model = tester.createModel(services, true, 1, "foo0");
            assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

            final Map<String, ContentCluster> contentClusters = model.getContentClusters();
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
            final String contentClusterName = contentCluster.getName();
            int i = 0;
            for (ContainerCluster containerCluster : containerClusters) {
                final String containerClusterName = containerCluster.getName();
                for (int j = 0; j < 2; j++) {
                    final Dispatch tld = contentCluster.getSearch().getIndexed().getTLDs().get(2 * i + j);
                    final Container container = containerCluster.getContainers().get(j);
                    final int containerConfigIdIndex = j + startIndexForContainerIds;

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

}
