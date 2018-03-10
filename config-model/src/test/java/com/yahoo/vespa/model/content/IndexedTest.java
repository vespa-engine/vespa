// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.messagebus.routing.RouteSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.routing.DocumentProtocol;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Test for using the content model to create indexed search clusters.
 */
public class IndexedTest extends ContentBaseTest {
    private String createVespaServices(String pre, List<String> sdNames, String post, String mode) {
        StringBuilder retval = new StringBuilder();
        retval.append(pre);


        for (String sdName : sdNames) {
            retval.append("<document type='" + sdName + "' " + "mode='" + mode + "'/>");
        }

        retval.append(post);
        return retval.toString();
    }
    private String createProtonIndexedVespaServices(List<String> sdNames) {
        String pre = "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services version='1.0'>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0'/>" +
                "  </admin>" +
                "  <config name='vespa.configdefinition.specialtokens'>" +
                "    <tokenlist operation='append'>" +
                "      <name>default</name>" +
                "      <tokens operation='append'>" +
                "        <token>dvd+-r</token>" +
                "      </tokens>" +
                "    </tokenlist>" +
                "  </config>" +
                "  <jdisc version='1.0'>" +
                "    <search/>" +
                "    <nodes>" +
                "      <node hostalias='node0'/>" +
                "    </nodes>" +
                "  </jdisc>" +
                "  <content version='1.0' id='test'>" +
                "     <redundancy>1</redundancy>" +
                "     <engine>" +
                "        <proton>" +
                "           <visibility-delay>34</visibility-delay>" +
                "        </proton>" +
                "     </engine>" +
                "     <documents>";

        String post = "         </documents>" +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='3' />" +
                "     </group>" +
                "</content>" +
                "</services>";
        return createVespaServices(pre, sdNames, post, "index");
    }
    private String createProtonStreamingVespaServices(List<String> sdNames) {
        String pre = "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services version='1.0'>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0'/>" +
                "  </admin>" +
                "  <jdisc version='1.0'>" +
                "    <search/>" +
                "    <nodes>" +
                "      <node hostalias='node0'/>" +
                "    </nodes>" +
                "  </jdisc>" +
                "  <content version='1.0' id='test'>" +
                "     <redundancy>1</redundancy>\n" +
                "     <engine>" +
                "       <proton/>" +
                "     </engine>" +
                "     <documents>";
        String post =
                "         </documents>" +
                        "     <group>" +
                        "       <node hostalias='node0' distribution-key='3' />" +
                        "     </group>" +
                        "</content>" +
                        "</services>";
        return createVespaServices(pre, sdNames, post, "streaming");
    }

    private VespaModel getIndexedVespaModel() {
        return getIndexedVespaModelCreator().create();
    }

    private VespaModelCreatorWithMockPkg getIndexedVespaModelCreator() {
        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("type1", "type2", "type3");
        return new VespaModelCreatorWithMockPkg(getHosts(), createProtonIndexedVespaServices(Arrays.asList("type1", "type2", "type3")), sds);
    }

    private VespaModel getStreamingVespaModel() {
        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("type1");
        return new VespaModelCreatorWithMockPkg(getHosts(), createProtonStreamingVespaServices(Arrays.asList("type1")), sds).create();
    }

    @Test
    public void requireMultipleDocumentTypes() {
        VespaModelCreatorWithMockPkg creator = getIndexedVespaModelCreator();
        VespaModel model = creator.create();
        DeployState deployState = creator.deployState;
        IndexedSearchCluster cluster = model.getContentClusters().get("test").getSearch().getIndexed();
        assertEquals(3, cluster.getDocumentDbs().size());
        NewDocumentType type1 = deployState.getDocumentModel().getDocumentManager().getDocumentType("type1");
        NewDocumentType type2 = deployState.getDocumentModel().getDocumentManager().getDocumentType("type2");
        NewDocumentType type3 = deployState.getDocumentModel().getDocumentManager().getDocumentType("type3");
        assertNotNull(type1);
        assertNotNull(type2);
        assertNotNull(type3);
    }

    @Test
    public void requireIndexedOnlyServices() {
        VespaModel model = getIndexedVespaModel();
        HostResource h = model.getHostSystem().getHosts().get(0);
        String [] expectedServices = {"logserver", "configserver", "adminserver", "slobrok",
                                      "logd", "configproxy","config-sentinel",
                                      "qrserver", "fleetcontroller", "topleveldispatch", "docprocservice",
                                      "storagenode", "searchnode", "distributor", "transactionlogserver"};
        // TODO DomContentBuilderTest.assertServices(h, expectedServices);
        Routing routing = model.getRouting();
        assertNotNull(routing);
        assertEquals("[]", routing.getErrors().toString());
        assertEquals(1, routing.getProtocols().size());
        DocumentProtocol protocol = (DocumentProtocol) routing.getProtocols().get(0);
        RoutingTableSpec spec = protocol.getRoutingTableSpec();
        assertEquals(2, spec.getNumHops());
        assertEquals("docproc/cluster.test.indexing/chain.indexing", spec.getHop(0).getName());
        assertEquals("indexing", spec.getHop(1).getName());

        RouteSpec r;
        r = spec.getRoute(0);
        assertEquals("default", r.getName());
        assertEquals(1, r.getNumHops());
        assertEquals("indexing", r.getHop(0));
        r = spec.getRoute(1);
        assertEquals("storage/cluster.test", r.getName());
        assertEquals(1, r.getNumHops());
        assertEquals("route:test", r.getHop(0));
        r = spec.getRoute(2);
        assertEquals("test", r.getName());
        assertEquals(1, r.getNumHops());
        assertEquals("[MessageType:test]", r.getHop(0));
        r = spec.getRoute(3);
        assertEquals("test-direct", r.getName());
        assertEquals(1, r.getNumHops());
        assertEquals("[Content:cluster=test]", r.getHop(0));
        r = spec.getRoute(4);
        assertEquals("test-index", r.getName());
        assertEquals(2, r.getNumHops());
        assertEquals("docproc/cluster.test.indexing/chain.indexing", r.getHop(0));
        assertEquals("[Content:cluster=test]", r.getHop(1));
    }
    @Test
    public void requireProtonStreamingOnly()
    {
        VespaModel model = getStreamingVespaModel();
        HostResource h = model.getHostSystem().getHosts().get(0);
        String [] expectedServices = {"logserver", "configserver", "adminserver", "slobrok",
                                      "logd", "configproxy","config-sentinel",
                                      "qrserver", "storagenode", "searchnode", "distributor",
                                      "transactionlogserver"};
// TODO        DomContentBuilderTest.assertServices(h, expectedServices);
        ContentCluster s = model.getContentClusters().get("test");
        assertFalse(s.getSearch().hasIndexedCluster());


        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        s.getStorageNodes().getConfig(builder);
        s.getStorageNodes().getChildren().get("3").getConfig(builder);
    }

    @Test
    public void requireCorrectClusterList()
    {
        VespaModel model = getStreamingVespaModel();
        ContentCluster s = model.getContentClusters().get("test");
        assertNotNull(s);
        assertFalse(s.getSearch().hasIndexedCluster());
        ClusterListConfig config = model.getConfig(ClusterListConfig.class, VespaModel.ROOT_CONFIGID);
        assertThat(config.storage().size(), is(1));
        assertThat(config.storage(0).name(), is("test"));
        assertThat(config.storage(0).configid(), is("test"));
    }

    @Test
    public void testContentSummaryStore() {
        String services= 
                "<services version='1.0'>" +
                "<admin version='2.0'><adminserver hostalias='node0' /></admin>" +
                "<content id='docstore' version='1.0'>\n" + 
                "    <redundancy>1</redundancy>\n" + 
                "    <documents>\n" + 
                "      <document mode='index' type='docstorebench'/>\n" + 
                "    </documents>\n" + 
                "    <group>\n" +
                "      <node distribution-key='0' hostalias='node0'/>\n" + 
                "    </group>\n" + 
                "    <engine>\n" + 
                "      <proton>\n" + 
                "        <searchable-copies>1</searchable-copies>\n" + 
                "        <tuning>\n" + 
                "          <searchnode>\n" + 
                "            <summary>\n" + 
                "              <store>\n" + 
                "                <logstore>\n" + 
                "                  <chunk>\n" + 
                "                    <maxsize>2048</maxsize>\n" + 
                "                  </chunk>\n" + 
                "                </logstore>\n" + 
                "              </store>\n" + 
                "            </summary>\n" + 
                "          </searchnode>\n" + 
                "        </tuning>\n" + 
                "      </proton>\n" + 
                "    </engine>\n" + 
                "  </content>\n" +
                "  </services>";

        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("docstorebench");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), services, sds).create();
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        model.getConfig(pb, "docstore/search/cluster.docstore/0");
    }

    @Test
    public void testMixedIndexAndStoreOnly() {
        String services=
                "<services version='1.0'>" +
                "  <admin version='2.0'><adminserver hostalias='node0' /></admin>" +
                "  <content id='docstore' version=\"1.0\">" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document type=\"index_me\" mode=\"index\"/>" +
                "      <document type=\"store_me\" mode=\"store-only\"/>" +
                "    </documents>" +
                "    <group>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </group>" +
                "  </content>" +
                "</services>";

        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("index_me", "store_me");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), services, sds).create();
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        model.getConfig(pb, "docstore/search/cluster.docstore/0");
        ProtonConfig protonConfig = new ProtonConfig(pb);
        assertEquals(2, protonConfig.documentdb().size());
        assertEquals("index_me", protonConfig.documentdb(0).inputdoctypename());
        assertEquals("docstore/search/cluster.docstore/index_me", protonConfig.documentdb(0).configid());
        assertEquals("store_me", protonConfig.documentdb(1).inputdoctypename());
        assertEquals("docstore/search", protonConfig.documentdb(1).configid());
    }

    @Test
    public void requireThatIndexingDocprocGetsConfigIdBasedOnDistributionKey() {
        VespaModel model = getIndexedVespaModel();
        ContainerCluster cluster = model.getContainerClusters().get("cluster.test.indexing");
        assertEquals("docproc/cluster.test.indexing/3", cluster.getContainers().get(0).getConfigId());
    }
}
