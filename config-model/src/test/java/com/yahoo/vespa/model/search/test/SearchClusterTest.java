// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.component.ComponentId;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.document.DataType;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * Unit tests for SearchCluster. Please use this instead of SearchModelTestCase if possible and
 * write _unit_ tests. Thanks.
 *
 * @author hmusum
 */
public class SearchClusterTest {

    @Test
    public void testSdConfigLogical() {
        // sd1
        SDDocumentType sdt1 = new SDDocumentType("s1");
        Search search1 = new Search("s1", null);
        SDField f1 = new SDField("f1", DataType.STRING);
        f1.addAttribute(new Attribute("f1", DataType.STRING));
        f1.setIndexingScript(new ScriptExpression(new StatementExpression(new AttributeExpression("f1"))));
        sdt1.addField(f1);
        search1.addDocument(sdt1);

        // sd2
        SDDocumentType sdt2 = new SDDocumentType("s2");
        Search search2 = new Search("s2", null);
        SDField f2=new SDField("f2", DataType.STRING);
        f2.addAttribute(new Attribute("f2", DataType.STRING));
        f2.setIndexingScript(new ScriptExpression(new StatementExpression(new AttributeExpression("f2"))));
        sdt2.addField(f2);
        search2.addDocument(sdt2);

        SearchBuilder builder = new SearchBuilder();
        builder.importRawSearch(search1);
        builder.importRawSearch(search2);
        builder.build();
    }

    @Test
    public void search_model_is_connected_to_container_clusters_two_content_clusters() {
        String vespaHosts = "<?xml version='1.0' encoding='utf-8' ?>" +
                            "<hosts>" +
                            "  <host name='node0host'>" +
                            "    <alias>node0</alias>" +
                            "  </host>" +
                            "  <host name='node1host'>" +
                            "    <alias>node1</alias>" +
                            "  </host>" +
                            "  <host name='node2host'>" +
                            "    <alias>node2</alias>" +
                            "  </host>" +
                            "</hosts>";

        String services =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<services version=\"1.0\">" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0' />" +
                "  </admin>\n" +
                "  <container version='1.0' id='j1'>\n" +
                "    <search>" +
                "      <chain id='s1Chain'>" +
                "        <searcher id='S1ClusterSearcher'/>" +
                "      </chain>" +
                "      <provider cluster='normal' id='normal' type='local'/>\n" +
                "    </search>" +
                "    <nodes>" +
                "      <node hostalias=\"node0\" />" +
                "    </nodes>" +
                "  </container>" +

                "  <container version='1.0' id='j2'>" +
                "    <search>" +
                "      <chain id='s2Chain'>" +
                "        <searcher id='S2ClusterSearcher'/>" +
                "      </chain>" +
                "      <provider cluster='xbulk' id='xbulk' type='local'/>" +
                "    </search>" +
                "    <nodes>" +
                "      <node hostalias=\"node2\" />" +
                "    </nodes>" +
                "  </container>" +

                "  <content id='xbulk' version=\"1.0\">" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document mode='index' type=\"music\" />" +
                "     </documents>" +
                "     <nodes>" +
                "       <node hostalias=\"node0\" distribution-key=\"0\" />" +
                "     </nodes>" +
                "  </content>" +
                "  <content id=\"normal\" version='1.0'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document mode='index' type=\"music\" />" +
                "     </documents>" +
                "     <nodes>" +
                "       <node hostalias=\"node2\" distribution-key=\"0\" />" +
                "     </nodes>" +
                "  </content>" +
                "</services>";

        VespaModel model = new VespaModelCreatorWithMockPkg(vespaHosts, services, ApplicationPackageUtils.generateSearchDefinitions("music")).create();

        ContainerCluster containerCluster1 = (ContainerCluster)model.getConfigProducer("j1").get();
        assertFalse(containerCluster1.getSearch().getChains().localProviders().isEmpty());

        ContainerCluster containerCluster2 = (ContainerCluster)model.getConfigProducer("j2").get();
        assertFalse(containerCluster2.getSearch().getChains().localProviders().isEmpty());

        QrSearchersConfig.Builder builder = new QrSearchersConfig.Builder();
        containerCluster1.getConfig(builder);
        QrSearchersConfig config = new QrSearchersConfig(builder);

        assertEquals(2, config.searchcluster().size());
        int normalIndex = 0;
        int xbulkIndex = 1;
        assertEquals("normal", config.searchcluster().get(normalIndex).name());
        assertEquals("xbulk", config.searchcluster().get(xbulkIndex).name());

        ClusterConfig.Builder clusterConfigBuilder = new ClusterConfig.Builder();
        model.getConfig(clusterConfigBuilder, "j1/searchchains/chain/normal/component/com.yahoo.prelude.cluster.ClusterSearcher");
        ClusterConfig clusterConfig = new ClusterConfig(clusterConfigBuilder);
        assertEquals(normalIndex, clusterConfig.clusterId());
        assertEquals("normal", clusterConfig.clusterName());

        ClusterConfig.Builder clusterConfigBuilder2 = new ClusterConfig.Builder();
        model.getConfig(clusterConfigBuilder2, "j2/searchchains/chain/xbulk/component/com.yahoo.prelude.cluster.ClusterSearcher");
        ClusterConfig clusterConfig2 = new ClusterConfig(clusterConfigBuilder2);
        assertEquals(xbulkIndex, clusterConfig2.clusterId());
        assertEquals("xbulk", clusterConfig2.clusterName());

        AbstractSearchCluster searchCluster1 = model.getSearchClusters().get(normalIndex);
        assertEquals("normal", searchCluster1.getClusterName());
        assertEquals("normal/search/cluster.normal", searchCluster1.getConfigId());
        AbstractSearchCluster searchCluster2 = model.getSearchClusters().get(xbulkIndex);
        assertEquals("xbulk", searchCluster2.getClusterName());

        verifyDispatch(model, containerCluster1, "normal", "node2host");
        verifyDispatch(model, containerCluster1, "xbulk", "node0host");
    }

    private void verifyDispatch(VespaModel model, ContainerCluster containerCluster, String cluster, String host) {
        Component<?,?> dispatcher = (Component<?, ?>)containerCluster.getComponentsMap().get(new ComponentId("dispatcher." + cluster));
        assertNotNull(dispatcher);
        assertEquals("dispatcher." + cluster, dispatcher.getComponentId().stringValue());
        assertEquals("com.yahoo.search.dispatch.Dispatcher", dispatcher.getClassId().stringValue());
        assertEquals("j1/component/dispatcher." + cluster, dispatcher.getConfigId());
        DispatchConfig.Builder dispatchConfigBuilder = new DispatchConfig.Builder();
        model.getConfig(dispatchConfigBuilder, dispatcher.getConfigId());
        assertEquals(host, dispatchConfigBuilder.build().node(0).host());

        assertTrue(dispatcher.getInjectedComponentIds().contains("com.yahoo.search.dispatch.rpc.RpcResourcePool"));

        Component<?,?> rpcResourcePool = (Component<?, ?>)dispatcher.getChildren().get("com.yahoo.search.dispatch.rpc.RpcResourcePool");
        assertNotNull(rpcResourcePool);
        assertEquals("com.yahoo.search.dispatch.rpc.RpcResourcePool", rpcResourcePool.getComponentId().stringValue());
        assertEquals("com.yahoo.search.dispatch.rpc.RpcResourcePool", rpcResourcePool.getClassId().stringValue());
        assertEquals("j1/component/dispatcher." + cluster + "/com.yahoo.search.dispatch.rpc.RpcResourcePool", rpcResourcePool.getConfigId());
        dispatchConfigBuilder = new DispatchConfig.Builder();
        model.getConfig(dispatchConfigBuilder, rpcResourcePool.getConfigId());
        assertEquals(host, dispatchConfigBuilder.build().node(0).host());
    }

}
