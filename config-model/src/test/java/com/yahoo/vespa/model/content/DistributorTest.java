// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test for content DistributorCluster.
 */
public class DistributorTest {

    ContentCluster parseCluster(String xml) {
        try {
            List<String> searchDefs = ApplicationPackageUtils.generateSearchDefinitions("music", "movies", "bunnies");
            MockRoot root = ContentClusterUtils.createMockRoot(searchDefs);
            return ContentClusterUtils.createCluster(xml, root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    DistributorCluster parse(String xml) {
        return parseCluster(xml).getDistributorNodes();
    }

    @Test
    public void testBasics() {

        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        parse("<content id=\"foofighters\"><documents/>\n" +
              "  <group>" +
              "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
              "  </group>" +
              "</content>\n").
                getConfig(builder);

        StorServerConfig config = new StorServerConfig(builder);
        assertEquals(true, config.is_distributor());
        assertEquals("foofighters", config.cluster_name());
    }

    @Test
    public void testRevertDefaultOffForSearch() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents/>" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>").getConfig(builder);
        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);
        assertEquals(false, conf.enable_revert());
    }

    @Test
    public void testSplitAndJoin() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents/>" +
                "    <engine>" +
                "       <vds/>" +
                "    </engine>" +
                "    <tuning>\n" +
                "      <bucket-splitting max-documents=\"2K\" max-size=\"25M\" minimum-bits=\"8\" />\n" +
                "    </tuning>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>").getConfig(builder);

        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);

        assertEquals(2048, conf.splitcount());
        assertEquals(1024, conf.joincount());
        assertEquals(26214400, conf.splitsize());
        assertEquals(13107200, conf.joinsize());
        assertEquals(8, conf.minsplitcount());
        assertEquals(false, conf.inlinebucketsplitting());
    }

    @Test
    public void testThatGroupsAreCountedInWhenComputingSplitBits() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        ContentCluster cluster = parseCluster("<cluster id=\"storage\">\n" +
                "  <documents/>" +
                "    <engine>" +
                "       <vds/>" +
                "    </engine>" +
                "    <tuning>" +
                "      <distribution type=\"legacy\"/>" +
                "    </tuning>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "     <node distribution-key=\"1\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>");
        cluster.getConfig(builder);

        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);

        assertEquals(1024, conf.splitcount());
        assertEquals(512, conf.joincount());
        assertEquals(33544432, conf.splitsize());
        assertEquals(16000000, conf.joinsize());
        assertEquals(8, conf.minsplitcount());
        assertEquals(true, conf.inlinebucketsplitting());

        cluster = parseCluster("<cluster id=\"storage\">\n" +
                "  <documents/>" +
                "    <engine>" +
                "       <vds/>" +
                "    </engine>" +
                "    <tuning>" +
                "      <distribution type=\"legacy\"/>" +
                "    </tuning>\n" +
                "  <group>" +
                "    <distribution partitions=\"1|*\"/>" +
                "    <group name=\"a\" distribution-key=\"0\">" +
                "       <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "    </group>" +
                "    <group name=\"b\" distribution-key=\"1\">" +
                "       <node distribution-key=\"1\" hostalias=\"mockhost\"/>" +
                "    </group>" +
                "  </group>" +
                "</cluster>");
        cluster.getConfig(builder);

        conf = new StorDistributormanagerConfig(builder);

        assertEquals(1024, conf.splitcount());
        assertEquals(512, conf.joincount());
        assertEquals(33544432, conf.splitsize());
        assertEquals(16000000, conf.joinsize());
        assertEquals(1, conf.minsplitcount());
        assertEquals(true, conf.inlinebucketsplitting());
    }

    @Test
    public void testMaxMergesPerNode() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        DistributorCluster dcluster = parse("<content id=\"storage\">\n" +
                "  <documents/>" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</content>");
        ((ContentCluster) dcluster.getParent()).getConfig(builder);
        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);
        assertEquals(16, conf.maximum_nodes_per_merge());

        builder = new StorDistributormanagerConfig.Builder();
        dcluster = parse("<content id=\"storage\">\n" +
              "  <documents/>" +
              "  <tuning>\n" +
              "    <merges max-nodes-per-merge=\"4\"/>\n" +
              "  </tuning>\n" +
              "  <group>" +
              "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
              "  </group>" +
              "</content>");
        ((ContentCluster) dcluster.getParent()).getConfig(builder);
        conf = new StorDistributormanagerConfig(builder);
        assertEquals(4, conf.maximum_nodes_per_merge());
    }

    @Test
    public void testGarbageCollectionSetExplicitly() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
              "  <documents garbage-collection=\"true\">\n" +
              "    <document type=\"music\"/>\n" +
              "  </documents>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
              "</cluster>").getConfig(builder);

        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);
        assertEquals(3600, conf.garbagecollection().interval());
        assertEquals("not ((music))", conf.garbagecollection().selectiontoremove());
    }

    @Test
    public void testGarbageCollectionInterval() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents garbage-collection=\"true\" garbage-collection-interval=\"30\">\n" +
                "    <document type=\"music\"/>\n" +
                "  </documents>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>").getConfig(builder);

        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);
        assertEquals(30, conf.garbagecollection().interval());
    }

    @Test
    public void testGarbageCollectionOffByDefault() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents>\n" +
                "    <document type=\"music\"/>\n" +
                "  </documents>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>").getConfig(builder);

        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);
        assertEquals(0, conf.garbagecollection().interval());
        assertEquals("", conf.garbagecollection().selectiontoremove());
    }

    @Test
    public void testComplexGarbageCollectionSelectionForIndexedSearch() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse("<cluster id=\"foo\">\n" +
                "  <documents garbage-collection=\"true\" selection=\"true\">" +
                "    <document type=\"music\" selection=\"music.year &lt; now()\"/>\n" +
                "    <document type=\"movies\" selection=\"movies.year &lt; now() - 1200\"/>\n" +
                "  </documents>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>").getConfig(builder);

        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);
        assertEquals(3600, conf.garbagecollection().interval());
        assertEquals(
                "not ((true) and ((music and (music.year < now())) or (movies and (movies.year < now() - 1200))))",
                conf.garbagecollection().selectiontoremove());
    }

    @Test
    public void testGarbageCollectionDisabledIfForced() {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse("<cluster id=\"foo\">\n" +
                "  <documents selection=\"true\" garbage-collection=\"false\" garbage-collection-interval=\"30\">\n" +
                "    <document type=\"music\" selection=\"music.year &lt; now()\"/>\n" +
                "    <document type=\"movies\" selection=\"movies.year &lt; now() - 1200\"/>\n" +
                "  </documents>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>").getConfig(builder);

        StorDistributormanagerConfig conf = new StorDistributormanagerConfig(builder);
        assertEquals(0, conf.garbagecollection().interval());
        assertEquals("", conf.garbagecollection().selectiontoremove());
    }

    @Test
    public void testPortOverride() {
        StorCommunicationmanagerConfig.Builder builder = new StorCommunicationmanagerConfig.Builder();
        DistributorCluster cluster =
                parse("<cluster id=\"storage\" distributor-base-port=\"14065\">" +
                        "  <documents/>" +
                        "  <group>" +
                        "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                        "  </group>" +
                        "</cluster>");

        cluster.getChildren().get("0").getConfig(builder);
        StorCommunicationmanagerConfig config = new StorCommunicationmanagerConfig(builder);
        assertEquals(14066, config.rpcport());
    }

    private StorDistributormanagerConfig clusterXmlToConfig(String xml) {
        StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
        parse(xml).getConfig(builder);
        return new StorDistributormanagerConfig(builder);
    }

    private String generateXmlForDocTypes(DocType... docTypes) {
        return "<content id='storage'>\n" +
                DocType.listToXml(docTypes) +
               "\n</content>";
    }

    @Test
    public void bucket_activation_disabled_if_no_documents_in_indexed_mode() {
        StorDistributormanagerConfig config = clusterXmlToConfig(
                generateXmlForDocTypes(DocType.storeOnly("music")));
        assertThat(config.disable_bucket_activation(), is(true));
    }

    @Test
    public void bucket_activation_enabled_with_single_indexed_document() {
        StorDistributormanagerConfig config = clusterXmlToConfig(
                generateXmlForDocTypes(DocType.index("music")));
        assertThat(config.disable_bucket_activation(), is(false));
    }

    @Test
    public void bucket_activation_enabled_with_multiple_indexed_documents() {
        StorDistributormanagerConfig config = clusterXmlToConfig(
                generateXmlForDocTypes(DocType.index("music"),
                                       DocType.index("movies")));
        assertThat(config.disable_bucket_activation(), is(false));
    }

    @Test
    public void bucket_activation_enabled_if_at_least_one_document_indexed() {
        StorDistributormanagerConfig config = clusterXmlToConfig(
                generateXmlForDocTypes(DocType.storeOnly("music"),
                                       DocType.streaming("bunnies"),
                                       DocType.index("movies")));
        assertThat(config.disable_bucket_activation(), is(false));
    }

    @Test
    public void bucket_activation_disabled_for_single_streaming_type() {
        StorDistributormanagerConfig config = clusterXmlToConfig(
                generateXmlForDocTypes(DocType.streaming("music")));
        assertThat(config.disable_bucket_activation(), is(true));
    }

}
