// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ClusterTest {

    private static final double DELTA = 1E-12;

    @Test
    void requireThatContentSearchIsApplied() {
        ContentCluster cluster = newContentCluster(joinLines("<search>",
                "  <query-timeout>1.1</query-timeout>",
                "  <visibility-delay>2.3</visibility-delay>",
                "</search>"));
        IndexedSearchCluster searchCluster = cluster.getSearch().getIndexed();
        assertNotNull(searchCluster);
        assertEquals(1.1, searchCluster.getQueryTimeout(), DELTA);
        assertEquals(2.3, searchCluster.getVisibilityDelay(), DELTA);
        ProtonConfig proton = getProtonConfig(cluster);
        assertEquals(searchCluster.getVisibilityDelay(), proton.documentdb(0).visibilitydelay(), DELTA);
    }

    @Test
    void requireThatVisibilityDelayIsZeroForGlobalDocumentType() {
        ContentCluster cluster = newContentCluster(joinLines("<search>",
                "  <visibility-delay>2.3</visibility-delay>",
                "</search>"), true);
        ProtonConfig proton = getProtonConfig(cluster);
        assertEquals(0.0, proton.documentdb(0).visibilitydelay(), DELTA);
    }

    @Test
    void requireThatSearchCoverageIsApplied() {
        ContentCluster cluster = newContentCluster(joinLines("<search>",
                "  <coverage>",
                "    <minimum>0.11</minimum>",
                "    <min-wait-after-coverage-factor>0.23</min-wait-after-coverage-factor>",
                "    <max-wait-after-coverage-factor>0.58</max-wait-after-coverage-factor>",
                "  </coverage>",
                "</search>"));
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        cluster.getSearch().getConfig(builder);
        DispatchConfig config = new DispatchConfig(builder);
        assertEquals(11.0, config.minSearchCoverage(), DELTA);
        assertEquals(0.23, config.minWaitAfterCoverageFactor(), DELTA);
        assertEquals(0.58, config.maxWaitAfterCoverageFactor(), DELTA);
        assertEquals(2, config.searchableCopies());
        assertEquals(3, config.redundancy());
        assertEquals(DispatchConfig.DistributionPolicy.ADAPTIVE, config.distributionPolicy());
    }

    @Test
    void requireThatDispatchTuningIsApplied() {
        ContentCluster cluster = newContentCluster(joinLines("<search>", "</search>"),
                "",
                joinLines(
                        "<max-hits-per-partition>77</max-hits-per-partition>",
                        "<dispatch-policy>round-robin</dispatch-policy>",
                        "<min-active-docs-coverage>93</min-active-docs-coverage>",
                        "<top-k-probability>0.777</top-k-probability>"),
                false);
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        cluster.getSearch().getConfig(builder);
        DispatchConfig config = new DispatchConfig(builder);
        assertEquals(2, config.searchableCopies());
        assertEquals(3, config.redundancy());
        assertEquals(93.0, config.minActivedocsPercentage(), DELTA);
        assertEquals(DispatchConfig.DistributionPolicy.ROUNDROBIN, config.distributionPolicy());
        assertEquals(77, config.maxHitsPerNode());
        assertEquals(0.777, config.topKProbability(), DELTA);
    }

    @Test
    void requireThatDefaultDispatchConfigIsCorrect()  {
        ContentCluster cluster = newContentCluster(joinLines("<search>", "</search>"),
                joinLines("<tuning>", "</tuning>"));
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        cluster.getSearch().getConfig(builder);
        DispatchConfig config = new DispatchConfig(builder);
        assertEquals(2, config.searchableCopies());
        assertEquals(3, config.redundancy());
        assertEquals(DispatchConfig.DistributionPolicy.ADAPTIVE, config.distributionPolicy());
        assertEquals(1.0, config.maxWaitAfterCoverageFactor(), DELTA);
        assertEquals(0, config.minWaitAfterCoverageFactor(), DELTA);
        assertEquals(8, config.numJrtConnectionsPerNode());
        assertEquals(8, config.numJrtTransportThreads());
        assertEquals(100.0, config.minSearchCoverage(), DELTA);
        assertEquals(97.0, config.minActivedocsPercentage(), DELTA);
        assertEquals(0.9999, config.topKProbability(), DELTA);
        assertEquals(3, config.node().size());
        assertEquals(0, config.node(0).key());
        assertEquals(1, config.node(1).key());
        assertEquals(2, config.node(2).key());

        assertEquals(19106, config.node(0).port());
        assertEquals(19118, config.node(1).port());
        assertEquals(19130, config.node(2).port());

        assertEquals(0, config.node(0).group());
        assertEquals(0, config.node(1).group());
        assertEquals(0, config.node(2).group());

        assertEquals("localhost", config.node(0).host());
        assertEquals("localhost", config.node(1).host());
        assertEquals("localhost", config.node(2).host());
    }

    private static ContentCluster newContentCluster(String contentSearchXml, String searchNodeTuningXml) {
        return newContentCluster(contentSearchXml, searchNodeTuningXml, "", false);
    }

    private static ContentCluster newContentCluster(String contentSearchXml) {
        return newContentCluster(contentSearchXml, false);
    }

    private static ContentCluster newContentCluster(String contentSearchXml, boolean globalDocType) {
        return newContentCluster(contentSearchXml, "", "", globalDocType);
    }

    private static ContentCluster newContentCluster(String contentSearchXml, String searchNodeTuningXml,
                                                    String dispatchTuning, boolean globalDocType)
    {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts(joinLines(
                        "<hosts>",
                        "  <host name='localhost'><alias>my_host</alias></host>",
                        "</hosts>"))
                .withServices(joinLines(
                        "<services version='1.0'>",
                        "  <admin version='2.0'>",
                        "    <adminserver hostalias='my_host' />",
                        "  </admin>",
                        "<container id='foo' version='1.0'>",
                        "  <search />",
                        "  <nodes><node hostalias='my_host' /></nodes>",
                        "</container>",
                        "  <content version='1.0'>",
                        "    <redundancy>3</redundancy>",
                        "    <documents>",
                        "    " + getDocumentXml(globalDocType),
                        "    </documents>",
                        "    <engine>",
                        "      <proton>",
                        "        <searchable-copies>2</searchable-copies>",
                        searchNodeTuningXml,
                        "      </proton>",
                        "    </engine>",
                        "    <group>",
                        "      <node hostalias='my_host' distribution-key='0' />",
                        "      <node hostalias='my_host' distribution-key='1' />",
                        "      <node hostalias='my_host' distribution-key='2' />",
                        "    </group>",
                        contentSearchXml,
                        "    <tuning>",
                        "      <dispatch>",
                        dispatchTuning,
                        "      </dispatch>",
                        "    </tuning>",
                        "  </content>",
                        "</services>"))
                .withSchemas(ApplicationPackageUtils.generateSchemas("my_document"))
                .build();
        List<Content> contents = new TestDriver().buildModel(app).getConfigModels(Content.class);
        assertEquals(1, contents.size());
        return contents.get(0).getCluster();
    }

    private static String getDocumentXml(boolean globalDocType) {
        return "<document mode='index' type='my_document' " + (globalDocType ? "global='true' " : "") + "/>";
    }

    private static ProtonConfig getProtonConfig(ContentCluster cluster) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        cluster.getSearch().getConfig(builder);
        return new ProtonConfig(builder);
    }

}
