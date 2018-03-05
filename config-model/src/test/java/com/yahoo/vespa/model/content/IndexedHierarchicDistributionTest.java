// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.config.search.core.PartitionsConfig;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.DispatchGroup;
import com.yahoo.vespa.model.search.SearchInterface;
import com.yahoo.vespa.model.search.SearchNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.hamcrest.Matchers.containsString;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createCluster;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createClusterXml;
import static com.yahoo.vespa.model.search.utils.DispatchUtils.assertEngine;
import static com.yahoo.vespa.model.search.utils.DispatchUtils.getDataset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for hierarchic distribution in an indexed content cluster.
 *
 * @author geirst
 */
public class IndexedHierarchicDistributionTest {

    private ContentCluster addDispatcher(ContentCluster c) {
        c.getSearch().getIndexed().addTld(new SimpleConfigProducer(new MockRoot(""), ""), new HostResource(new Host(new MockRoot(""), "mockhost")));
        return c;
    }

    private ContentCluster getOneGroupCluster() throws Exception {
        String groupXml = joinLines("  <group>",
                          "    <node distribution-key='0' hostalias='mockhost'/>",
                          "    <node distribution-key='1' hostalias='mockhost'/>",
                          "    <node distribution-key='2' hostalias='mockhost'/>",
                          "  </group>", "");
        return addDispatcher(createCluster(createClusterXml(groupXml, 2, 2)));
    }

    private String getTwoGroupsXml(String partitions) {
        return joinLines("  <group>",
               "    <distribution partitions='" + partitions + "'/>",
               "    <group distribution-key='0' name='group0'>",
               "      <node distribution-key='0' hostalias='mockhost'/>",
               "      <node distribution-key='1' hostalias='mockhost'/>",
               "      <node distribution-key='2' hostalias='mockhost'/>",
               "    </group>",
               "    <group distribution-key='1' name='group1'>",
               "      <node distribution-key='3' hostalias='mockhost'/>",
               "      <node distribution-key='4' hostalias='mockhost'/>",
               "      <node distribution-key='5' hostalias='mockhost'/>",
               "    </group>",
               "  </group>", "");
    }

    private ContentCluster getTwoGroupsCluster() throws Exception {
        return addDispatcher(createCluster(createClusterXml(getTwoGroupsXml("3|*"), 6, 6)));
    }

    private ContentCluster getTwoGroupsCluster(int redundancy, int searchableCopies, String partitions) throws Exception {
        return addDispatcher(createCluster(createClusterXml(getTwoGroupsXml(partitions), redundancy, searchableCopies)));
    }

    private void assertSearchNode(int expRowId, int expPartitionId, int expDistibutionKey, SearchNode node) {
        assertEquals(expRowId, node.getNodeSpec().groupIndex());
        assertEquals(expPartitionId, node.getNodeSpec().partitionId());
        assertEquals(expDistibutionKey, ((ContentNode)node.getServiceLayerService()).getDistributionKey());
    }

    private StorDistributionConfig getStorDistributionConfig(ContentCluster c) {
        StorDistributionConfig.Builder b = new StorDistributionConfig.Builder();
        c.getConfig(b);
        return new StorDistributionConfig(b);
    }

    @Test
    public void requireThatSearchNodesAreCorrectWithOneGroup() throws Exception {
        ContentCluster c = getOneGroupCluster();
        List<SearchNode> searchNodes = c.getSearch().getSearchNodes();

        assertEquals(3, searchNodes.size());
        assertSearchNode(0, 0, 0, searchNodes.get(0));
        assertSearchNode(0, 1, 1, searchNodes.get(1));
        assertSearchNode(0, 2, 2, searchNodes.get(2));
    }

    @Test
    public void requireThatDispatcherIsCorrectWithOneGroup() throws Exception {
        ContentCluster c = getOneGroupCluster();
        PartitionsConfig.Dataset dataset = getDataset(c.getSearch().getIndexed().getTLDs().get(0));

        assertEquals(3, dataset.numparts());
        assertEquals(PartitionsConfig.Dataset.Querydistribution.AUTOMATIC, dataset.querydistribution());
        List<PartitionsConfig.Dataset.Engine> engines = dataset.engine();
        assertEquals(3, engines.size());
        assertEngine(0, 0, engines.get(0));
        assertEngine(0, 1, engines.get(1));
        assertEngine(0, 2, engines.get(2));
    }

    @Test
    public void requireThatActivePerLeafGroupIsDefaultWithOneGroup() throws Exception {
        ContentCluster c = getOneGroupCluster();
        assertFalse(getStorDistributionConfig(c).active_per_leaf_group());
    }

    @Test
    public void requireThatSearchNodesAreCorrectWithTwoGroups() throws Exception {
        ContentCluster c = getTwoGroupsCluster();
        List<SearchNode> searchNodes = c.getSearch().getSearchNodes();

        assertEquals(6, searchNodes.size());
        assertSearchNode(0, 0, 0, searchNodes.get(0));
        assertSearchNode(0, 1, 1, searchNodes.get(1));
        assertSearchNode(0, 2, 2, searchNodes.get(2));
        assertSearchNode(1, 0, 3, searchNodes.get(3));
        assertSearchNode(1, 1, 4, searchNodes.get(4));
        assertSearchNode(1, 2, 5, searchNodes.get(5));
    }

    @Test
    public void requireThatDispatcherIsCorrectWithTwoGroups() throws Exception {
        ContentCluster c = getTwoGroupsCluster();
        PartitionsConfig.Dataset dataset = getDataset(c.getSearch().getIndexed().getTLDs().get(0));

        assertEquals(3, dataset.numparts());
        assertEquals(2, dataset.maxnodesdownperfixedrow());
        assertEquals(PartitionsConfig.Dataset.Querydistribution.FIXEDROW, dataset.querydistribution());
        List<PartitionsConfig.Dataset.Engine> engines = dataset.engine();
        assertEquals(6, engines.size());
        assertEngine(0, 0, engines.get(0));
        assertEngine(1, 0, engines.get(1));
        assertEngine(0, 1, engines.get(2));
        assertEngine(1, 1, engines.get(3));
        assertEngine(0, 2, engines.get(4));
        assertEngine(1, 2, engines.get(5));
    }

    @Test
    public void requireThatActivePerLeafGroupIsSetWithTwoGroups() throws Exception {
        ContentCluster c = getTwoGroupsCluster();
        assertTrue(getStorDistributionConfig(c).active_per_leaf_group());
    }

    private ContentCluster getIllegalMultipleGroupsLevelCluster() throws Exception {
        String groupXml = joinLines("  <group>",
                "    <distribution partitions='2|*'/>",
                "    <group distribution-key='0' name='group0'>",
                "      <distribution partitions='1|*'/>",
                "      <group distribution-key='0' name='group00'>",
                "        <node distribution-key='0' hostalias='mockhost'/>",
                "      </group>",
                "      <group distribution-key='1' name='group01'>",
                "        <node distribution-key='1' hostalias='mockhost'/>",
                "      </group>",
                "    </group>",
                "  </group>", "");
        return createCluster(createClusterXml(groupXml, 2, 2));
    }

    private String getOddGroupsClusterXml() throws Exception {
        return joinLines("  <group>",
                "    <distribution partitions='2|*'/>",
                "    <group distribution-key='0' name='group0'>",
                "      <node distribution-key='0' hostalias='mockhost'/>",
                "    </group>",
                "    <group distribution-key='1' name='group1'>",
                "      <node distribution-key='1' hostalias='mockhost'/>",
                "      <node distribution-key='2' hostalias='mockhost'/>",
                "    </group>",
                "  </group>", "");
    }
    private ContentCluster getIllegalGroupsCluster() throws Exception {
        return createCluster(createClusterXml(getOddGroupsClusterXml(), 4, 4));
    }

    private String getRandomDispatchXml() {
        return joinLines("<tuning>",
                "  <dispatch>",
                "    <dispatch-policy>random</dispatch-policy>",
                "  </dispatch>",
                "</tuning>");
    }

    private ContentCluster getOddGroupsCluster() throws Exception {
        String groupXml = joinLines("  <group>",
                "    <distribution partitions='2|*'/>",
                "    <group distribution-key='0' name='group0'>",
                "      <node distribution-key='0' hostalias='mockhost'/>",
                "      <node distribution-key='1' hostalias='mockhost'/>",
                "    </group>",
                "    <group distribution-key='1' name='group1'>",
                "      <node distribution-key='3' hostalias='mockhost'/>",
                "      <node distribution-key='4' hostalias='mockhost'/>",
                "      <node distribution-key='5' hostalias='mockhost'/>",
                "    </group>",
                "  </group>", "");
        return createCluster(createClusterXml(groupXml, Optional.of(getRandomDispatchXml()), 4, 4));
    }

    @Test
    public void requireThatWeMustHaveOnlyOneGroupLevel() {
        try {
            getIllegalMultipleGroupsLevelCluster();
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("sub group 'group0' contains 2 sub groups."));
        }
    }

    @Test
    public void requireThatLeafGroupsMustHaveEqualNumberOfNodes() {
        try {
            getIllegalGroupsCluster();
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("leaf group 'group0' contains 1 node(s) while leaf group 'group1' contains 2 node(s)"));
        }
    }

    @Test
    public void requireThatLeafGroupsCanHaveUnequalNumberOfNodesIfRandomPolicy() throws Exception {
        ContentCluster c = getOddGroupsCluster();
        DispatchGroup dg = c.getSearch().getIndexed().getRootDispatch();
        assertEquals(8, dg.getRowBits());
        assertEquals(3, dg.getNumPartitions());
        assertEquals(true, dg.useFixedRowInDispatch());
        assertEquals(1, dg.getMaxNodesDownPerFixedRow());
        ArrayList<SearchInterface> list = new ArrayList<>();
        for(SearchInterface si : dg.getSearchersIterable()) {
            list.add(si);
        }
        assertEquals(5, list.size());
        assertEquals(0, list.get(0).getNodeSpec().partitionId());
        assertEquals(0, list.get(0).getNodeSpec().groupIndex());
        assertEquals(0, list.get(1).getNodeSpec().partitionId());
        assertEquals(1, list.get(1).getNodeSpec().groupIndex());
        assertEquals(1, list.get(2).getNodeSpec().partitionId());
        assertEquals(0, list.get(2).getNodeSpec().groupIndex());
        assertEquals(1, list.get(3).getNodeSpec().partitionId());
        assertEquals(1, list.get(3).getNodeSpec().groupIndex());
        assertEquals(2, list.get(4).getNodeSpec().partitionId());
        assertEquals(1, list.get(4).getNodeSpec().groupIndex());
    }

    @Test
    public void requireThatLeafGroupsCountMustBeAFactorOfRedundancy() {
        try {
            getTwoGroupsCluster(3, 3, "2|*");
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Expected number of leaf groups (2) to be a factor of redundancy (3)"));
        }
    }

    @Test
    public void requireThatRedundancyPerGroupMustBeIsEqual() {
        try {
            getTwoGroupsCluster(4, 4, "1|*");
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Expected distribution partitions should be '2|*'"));
        }
    }

    @Test
    public void requireThatReadyCopiesMustBeEqualToRedundancy() {
        try {
            getTwoGroupsCluster(4, 3, "2|*");
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Expected equal amount of ready copies per group"));
        }
    }

    @Test
    public void allowLessReadyCopiesThanRedundancy() throws Exception {
        getTwoGroupsCluster(4, 2, "2|*");
    }

    @Test
    public void allowNoReadyCopies() throws Exception {
        // The active one should be indexed anyhow. Setting up no ready copies
        getTwoGroupsCluster(4, 0, "2|*");
    }

}
