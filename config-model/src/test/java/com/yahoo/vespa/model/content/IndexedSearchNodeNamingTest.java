// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.SearchNode;
import org.junit.Test;

import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createCluster;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createClusterXml;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the naming of search nodes base dir and config ids in an indexed content cluster.
 *
 * @author geirst
 */
public class IndexedSearchNodeNamingTest {

    private ContentCluster getSingleNodeCluster() throws Exception {
        String groupXml = joinLines("  <group>",
                          "    <node distribution-key='3' hostalias='mockhost'/>",
                          "  </group>", "");
        return createCluster(createClusterXml(groupXml, 1, 1));
    }

    private ContentCluster getMultiNodeCluster() throws Exception {
        String groupXml = joinLines("  <group>",
                          "    <node distribution-key='5' hostalias='mockhost'/>",
                          "    <node distribution-key='3' hostalias='mockhost'/>",
                          "    <node distribution-key='7' hostalias='mockhost'/>",
                          "  </group>", "");
        return createCluster(createClusterXml(groupXml, 1, 1));
    }

    private ContentCluster getMultiGroupCluster() throws Exception {
        String groupXml = joinLines("  <group>",
                          "    <distribution partitions='1|*'/>",
                          "    <group distribution-key='3' name='group0'>",
                          "      <node distribution-key='7' hostalias='mockhost'/>",
                          "      <node distribution-key='11' hostalias='mockhost'/>",
                          "    </group>",
                          "    <group distribution-key='5' name='group1'>",
                          "      <node distribution-key='17' hostalias='mockhost'/>",
                          "      <node distribution-key='13' hostalias='mockhost'/>",
                          "    </group>",
                          "  </group>", "");
        return createCluster(createClusterXml(groupXml, 2, 2));
    }

    private void assertBaseDir(String expected, SearchNode node) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        node.getConfig(builder);
        ProtonConfig cfg = new ProtonConfig(builder);
        assertEquals(expected, cfg.basedir());
    }

    private void assertConfigId(String expected, SearchNode node) {
        assertEquals(expected, node.getConfigId());
    }

    private void assertSearchNode(String expName, String expId, SearchNode node) {
        assertBaseDir(Defaults.getDefaults().underVespaHome("var/db/vespa/search/cluster.mycluster/" + expName), node);
        assertConfigId("mycluster/search/cluster.mycluster/" + expId, node);
    }

    @Test
    public void requireThatSingleNodeIsNamedAfterDistributionKey() throws Exception {
        ContentCluster cluster = getSingleNodeCluster();
        List<SearchNode> nodes = cluster.getSearch().getSearchNodes();
        assertSearchNode("n3", "3", nodes.get(0));
    }

    @Test
    public void requireThatMultipleNodesAreNamedAfterDistributionKey() throws Exception {
        ContentCluster cluster = getMultiNodeCluster();
        List<SearchNode> nodes = cluster.getSearch().getSearchNodes();
        assertEquals(3, nodes.size());
        assertSearchNode("n5", "5", nodes.get(0));
        assertSearchNode("n3", "3", nodes.get(1));
        assertSearchNode("n7", "7", nodes.get(2));
    }

    @Test
    public void requireThatNodesInHierarchicGroupsAreNamedAfterDistributionKey() throws Exception {
        ContentCluster cluster = getMultiGroupCluster();
        List<SearchNode> nodes = cluster.getSearch().getSearchNodes();
        assertEquals(4, nodes.size());
        assertSearchNode("n7", "7", nodes.get(0));
        assertSearchNode("n11", "11", nodes.get(1));
        assertSearchNode("n17", "17", nodes.get(2));
        assertSearchNode("n13", "13", nodes.get(3));
    }
}
