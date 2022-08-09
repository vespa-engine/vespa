// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.routing.DocumentProtocol;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
                "  <container version='1.0'>" +
                "    <search/>" +
                "    <nodes>" +
                "      <node hostalias='node0'/>" +
                "    </nodes>" +
                "  </container>" +
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
                "  <container version='1.0'>" +
                "    <search/>" +
                "    <nodes>" +
                "      <node hostalias='node0'/>" +
                "    </nodes>" +
                "  </container>" +
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
        List<String> sds = ApplicationPackageUtils.generateSchemas("type1", "type2", "type3");
        return new VespaModelCreatorWithMockPkg(getHosts(), createProtonIndexedVespaServices(List.of("type1", "type2", "type3")), sds);
    }

    private VespaModel getStreamingVespaModel() {
        List<String> sds = ApplicationPackageUtils.generateSchemas("type1");
        return new VespaModelCreatorWithMockPkg(getHosts(), createProtonStreamingVespaServices(List.of("type1")), sds).create();
    }

    @Test
    void requireMultipleDocumentTypes() {
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
    void requireIndexedOnlyServices() {
        VespaModel model = getIndexedVespaModel();
        // TODO
        // HostResource h = model.getHostSystem().getHosts().get(0);
        // String [] expectedServices = {"logserver", "configserver", "adminserver", "slobrok",
        //                               "logd", "configproxy","config-sentinel",
        //                               "container", "fleetcontroller",
        //                               "storagenode", "searchnode", "distributor", "transactionlogserver"};
        // DomContentBuilderTest.assertServices(h, expectedServices);
        Routing routing = model.getRouting();
        assertNotNull(routing);
        assertEquals("[]", routing.getErrors().toString());
        assertEquals(1, routing.getProtocols().size());
        DocumentProtocol protocol = (DocumentProtocol) routing.getProtocols().get(0);
        RoutingTableSpec spec = protocol.getRoutingTableSpec();
        assertEquals(2, spec.getNumHops());

        assertEquals("container/chain.indexing", spec.getHop(0).getName());
        assertEquals("indexing", spec.getHop(1).getName());

        assertRoute(spec.getRoute(0), "default", "indexing");
        assertRoute(spec.getRoute(1), "default-get", "[Content:cluster=test]");
        assertRoute(spec.getRoute(2), "storage/cluster.test", "route:test");
        assertRoute(spec.getRoute(3), "test", "[MessageType:test]");
        assertRoute(spec.getRoute(4), "test-direct", "[Content:cluster=test]");
        assertRoute(spec.getRoute(5), "test-index", "container/chain.indexing", "[Content:cluster=test]");
    }

    @Test
    void requireProtonStreamingOnly() {
        VespaModel model = getStreamingVespaModel();
        // TODO
        // HostResource h = model.getHostSystem().getHosts().get(0);
        // String [] expectedServices = {"logserver", "configserver", "adminserver", "slobrok",
        //                               "logd", "configproxy","config-sentinel",
        //                               "container", "storagenode", "searchnode", "distributor",
        //                               "transactionlogserver"};
        // DomContentBuilderTest.assertServices(h, expectedServices);
        ContentCluster s = model.getContentClusters().get("test");
        assertFalse(s.getSearch().hasIndexedCluster());

        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        s.getStorageCluster().getConfig(builder);
        s.getStorageCluster().getChildren().get("3").getConfig(builder);
    }

    @Test
    void requireCorrectClusterList() {
        VespaModel model = getStreamingVespaModel();
        ContentCluster s = model.getContentClusters().get("test");
        assertNotNull(s);
        assertFalse(s.getSearch().hasIndexedCluster());
        ClusterListConfig config = model.getConfig(ClusterListConfig.class, VespaModel.ROOT_CONFIGID);
        assertEquals(1, config.storage().size());
        assertEquals("test", config.storage(0).name());
        assertEquals("test", config.storage(0).configid());
    }

    @Test
    void testContentSummaryStore() {
        String services =
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

        List<String> sds = ApplicationPackageUtils.generateSchemas("docstorebench");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), services, sds).create();
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        model.getConfig(pb, "docstore/search/cluster.docstore/0");
    }

    @Test
    void testMixedIndexAndStoreOnly() {
        String services =
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

        List<String> sds = ApplicationPackageUtils.generateSchemas("index_me", "store_me");
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
    void requireThatIndexingDocprocGetsConfigIdBasedOnDistributionKey() {
        VespaModel model = getIndexedVespaModel();
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
        assertEquals("container/container.0", cluster.getContainers().get(0).getConfigId());
    }
}
