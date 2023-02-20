// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.DispatchGroup;
import com.yahoo.vespa.model.search.SearchInterface;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createCluster;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createClusterXml;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for hierarchic distribution in an indexed content cluster.
 *
 * @author geirst
 */
public class IndexedHierarchicDistributionTest {

    private ContentCluster getOneGroupCluster() throws Exception {
        String groupXml = joinLines("  <group>",
                          "    <node distribution-key='0' hostalias='mockhost'/>",
                          "    <node distribution-key='1' hostalias='mockhost'/>",
                          "    <node distribution-key='2' hostalias='mockhost'/>",
                          "  </group>", "");
        return createCluster(createClusterXml(groupXml, 2, 2));
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
        return createCluster(createClusterXml(getTwoGroupsXml("3|*"), 6, 6));
    }

    private ContentCluster getTwoGroupsCluster(int redundancy, int searchableCopies, String partitions) throws Exception {
        return createCluster(createClusterXml(getTwoGroupsXml(partitions), redundancy, searchableCopies));
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
    void requireThatSearchNodesAreCorrectWithOneGroup() throws Exception {
        ContentCluster c = getOneGroupCluster();
        List<SearchNode> searchNodes = c.getSearch().getSearchNodes();

        assertEquals(3, searchNodes.size());
        assertSearchNode(0, 0, 0, searchNodes.get(0));
        assertSearchNode(0, 1, 1, searchNodes.get(1));
        assertSearchNode(0, 2, 2, searchNodes.get(2));
    }

    @Test
    void requireThatActivePerLeafGroupIsDefaultWithOneGroup() throws Exception {
        ContentCluster c = getOneGroupCluster();
        assertFalse(getStorDistributionConfig(c).active_per_leaf_group());
    }

    @Test
    void requireThatSearchNodesAreCorrectWithTwoGroups() throws Exception {
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
    void requireThatActivePerLeafGroupIsSetWithTwoGroups() throws Exception {
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

    private String getOddGroupsClusterXml() {
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
        return createCluster(createClusterXml(getOddGroupsClusterXml(), Optional.of(getRoundRobinDispatchXml()), 4, 4));
    }

    private String getRoundRobinDispatchXml() {
        return joinLines("<tuning>",
                "  <dispatch>",
                "    <dispatch-policy>round-robin</dispatch-policy>",
                "  </dispatch>",
                "</tuning>");
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
    void requireThatWeMustHaveOnlyOneGroupLevel() {
        try {
            getIllegalMultipleGroupsLevelCluster();
            fail("Did not get expected Exception");
        } catch (Exception e) {
            assertEquals("Expected all groups under root group 'null' to be leaf groups only containing nodes, but sub group 'group0' contains 2 sub groups",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatLeafGroupsMustHaveEqualNumberOfNodes() {
        try {
            getIllegalGroupsCluster();
            fail("Did not get expected Exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("leaf group 'group0' contains 1 node(s) while leaf group 'group1' contains 2 node(s)"));
        }
    }

    @Test
    void requireThatLeafGroupsCanHaveUnequalNumberOfNodesIfRandomPolicy() throws Exception {
        ContentCluster c = getOddGroupsCluster();
        DispatchGroup dg = c.getSearch().getIndexed().getRootDispatch();
        assertEquals(8, dg.getRowBits());
        assertEquals(3, dg.getNumPartitions());
        assertTrue(dg.useFixedRowInDispatch());
        ArrayList<SearchInterface> list = new ArrayList<>();
        for (SearchInterface si : dg.getSearchersIterable()) {
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
    void requireThatLeafGroupsCountMustBeAFactorOfRedundancy() {
        try {
            getTwoGroupsCluster(3, 3, "2|*");
            fail("Did not get expected Exception");
        } catch (Exception e) {
            assertEquals("In content cluster 'mycluster': Expected number of leaf groups (2) to be a factor of redundancy (3), but it is not",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatRedundancyPerGroupMustBeIsEqual() {
        try {
            getTwoGroupsCluster(4, 4, "1|*");
            fail("Did not get expected Exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Expected distribution partitions should be '2|*'"));
        }
    }

    @Test
    void requireThatReadyCopiesMustBeEqualToRedundancy() {
        try {
            getTwoGroupsCluster(4, 3, "2|*");
            fail("Did not get expected Exception");
        } catch (Exception e) {
            assertEquals("In content cluster 'mycluster': Expected equal amount of ready copies per group, but 3 ready copies is specified with 2 groups", Exceptions.toMessageString(e));
        }
    }

    @Test
    void allowLessReadyCopiesThanRedundancy() throws Exception {
        getTwoGroupsCluster(4, 2, "2|*");
    }

}
