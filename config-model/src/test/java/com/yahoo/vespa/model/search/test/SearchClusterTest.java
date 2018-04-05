// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.document.DataType;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 *
 * Unit tests for SearchCluster. Please use this instead of SearchModelTestCase if possible and
 * write _unit_ tests. Thanks.
 *
 * @author hmusum
 */
public class SearchClusterTest {

    private String vespaHosts = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<hosts>  " +
            "<host name=\"foo\">" +
            "<alias>node0</alias>" +
            "</host>" +
            "<host name=\"bar\">" +
            "<alias>node1</alias>" +
            "</host>" +
            "<host name=\"baz\">" +
            "<alias>node2</alias>" +
            "</host>" +
            "</hosts>";

    @Test
    public void testSdConfigLogical() throws IOException, SAXException {
        // sd1
        SDDocumentType sdt1=new SDDocumentType("s1");
        Search search1 = new Search("s1", null);
        SDField f1=new SDField("f1", DataType.STRING);
        f1.addAttribute(new Attribute("f1", DataType.STRING));
        f1.setIndexingScript(new ScriptExpression(new StatementExpression(new AttributeExpression("f1"))));
        sdt1.addField(f1);
        search1.addDocument(sdt1);

        // sd2
        SDDocumentType sdt2=new SDDocumentType("s2");
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
    public void search_model_is_connected_to_container_clusters_two_content_clusters() throws Exception {
        String services = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<services version=\"1.0\">" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0' />" +
                "  </admin>\n" +
                "  <jdisc version='1.0' id='j1'>\n" +
                "    <search>" +
                "      <chain id='s1Chain'>" +
                "        <searcher id='S1ClusterSearcher'/>" +
                "      </chain>" +
                "      <provider cluster='normal' id='normal' type='local'/>\n" +
                "    </search>" +
                "    <nodes>" +
                "      <node hostalias=\"node0\" />" +
                "    </nodes>" +
                "  </jdisc>" +

                "  <jdisc version='1.0' id='j2'>" +
                "    <search>" +
                "      <chain id='s2Chain'>" +
                "        <searcher id='S2ClusterSearcher'/>" +
                "      </chain>" +
                "      <provider cluster='xbulk' id='xbulk' type='local'/>" +
                "    </search>" +
                "    <nodes>" +
                "      <node hostalias=\"node2\" />" +
                "    </nodes>" +
                "  </jdisc>" +

                "  <content id='xbulk' version=\"1.0\">" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document mode='index' type=\"music\" />" +
                "     </documents>" +
                "         <nodes>" +
                "            <node hostalias=\"node0\" distribution-key=\"0\" />" +
                "         </nodes>" +
                "  </content>" +
                "  <content id=\"normal\" version='1.0'>" +
                "     <redundancy>2</redundancy>" +
                "     <documents>" +
                "       <document mode='index' type=\"music\" />" +
                "     </documents>" +
                "         <nodes>" +
                "            <node hostalias=\"node2\" distribution-key=\"0\" />" +
                "         </nodes>" +
                "  </content>" +
                "</services>";

        VespaModel model = new VespaModelCreatorWithMockPkg(vespaHosts, services, ApplicationPackageUtils.generateSearchDefinitions("music")).create();

        ContainerCluster cluster1 = (ContainerCluster)model.getConfigProducer("j1").get();
        assertFalse(cluster1.getSearch().getChains().localProviders().isEmpty());

        ContainerCluster cluster2 = (ContainerCluster)model.getConfigProducer("j2").get();
        assertFalse(cluster2.getSearch().getChains().localProviders().isEmpty());

        QrSearchersConfig.Builder builder = new QrSearchersConfig.Builder();
        cluster1.getContainers().get(0).getConfig(builder);
        QrSearchersConfig config = new QrSearchersConfig(builder);

        String hostName = cluster1.getContainers().get(0).getHostName();
        assertThat(config.searchcluster().size(), is(2));
        int normalId = 0;
        int bulkId = 1;
        assertThat(config.searchcluster().get(normalId).name(), is("normal"));
        assertThat(config.searchcluster().get(bulkId).name(), is("xbulk"));
        assertEquals(1, config.searchcluster(0).dispatcher().size());
        assertEquals(hostName, config.searchcluster(0).dispatcher(0).host());
        assertEquals(19129, config.searchcluster(0).dispatcher(0).port());

        assertEquals(1, config.searchcluster(1).dispatcher().size());
        assertEquals(hostName, config.searchcluster(1).dispatcher(0).host());
        assertEquals(19132, config.searchcluster(1).dispatcher(0).port());


        ClusterConfig.Builder clusterConfigBuilder = new ClusterConfig.Builder();
        model.getConfig(clusterConfigBuilder, "j1/searchchains/chain/normal/component/com.yahoo.prelude.cluster.ClusterSearcher");
        ClusterConfig clusterConfig = new ClusterConfig(clusterConfigBuilder);
        assertThat(clusterConfig.clusterId(), is(normalId));
        assertThat(clusterConfig.clusterName(), is("normal"));

        ClusterConfig.Builder clusterConfigBuilder2 = new ClusterConfig.Builder();
        model.getConfig(clusterConfigBuilder2, "j2/searchchains/chain/xbulk/component/com.yahoo.prelude.cluster.ClusterSearcher");
        ClusterConfig clusterConfig2 = new ClusterConfig(clusterConfigBuilder2);
        assertThat(clusterConfig2.clusterId(), is(bulkId));
        assertThat(clusterConfig2.clusterName(), is("xbulk"));
    }

}
