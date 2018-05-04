// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

import org.junit.Test;

import java.text.ParseException;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GroupTestCase {

    private void assertDistribution(String spec, int redundancy, String expectedResult) throws ParseException {
        Group.Distribution distribution = new Group.Distribution(spec, redundancy);
        assertEquals(spec, distribution.toString());
        int[] resultArray = distribution.getRedundancyArray(redundancy);
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<resultArray.length; ++i) {
            if (i != 0) sb.append(',');
            sb.append(resultArray[i]);
        }
        assertEquals("Spec: \"" + spec + "\", redundancy " + redundancy + " got unexpected result", expectedResult, sb.toString());
    }

    private void assertDistributionFailure(String spec, int redundancy, String expectedError) {
        try{
            Group.Distribution distribution = new Group.Distribution(spec, redundancy);
            assertTrue("Failed to fail parsing of spec \"" + spec + "\", redundancy " + redundancy + " with failure: " + expectedError, false);
        } catch (Exception e) {
            assertEquals(expectedError, e.getMessage());
        }
    }

    @Test
    public void testStarConversion() throws ParseException {
        assertDistribution("1|*|*", 5, "2,2,1");
        assertDistribution("1|*|*", 6, "3,2,1");
        assertDistribution("1|*|*", 3, "1,1,1");
        assertDistribution("1|*|*", 2, "1,1");
        assertDistribution("4|*", 3, "3");
        assertDistribution("2|*", 3, "2,1");
        assertDistribution("*|*", 3, "2,1");
        assertDistribution("*|*|*", 4, "2,1,1");
        assertDistribution("*|*|*", 5, "2,2,1");
        assertDistribution("*|*|*|*", 5, "2,1,1,1");

        assertDistributionFailure("2|*", 0, "The max redundancy must be a positive number in the range 1-255.");
        assertDistributionFailure("*|2", 3, "Illegal distribution spec \"*|2\". Asterix specification must be tailing the specification.");
        assertDistributionFailure("*|2|*", 3, "Illegal distribution spec \"*|2|*\". Asterix specification must be tailing the specification.");
        assertDistributionFailure("0|*", 3, "Illegal distribution spec \"0|*\". Copy counts must be in the range 1-255.");
        assertDistributionFailure("1|0|*", 3, "Illegal distribution spec \"1|0|*\". Copy counts must be in the range 1-255.");
        assertDistributionFailure("1|a|*", 3, "Illegal distribution spec \"1|a|*\". Copy counts must be integer values in the range 1-255.");
        assertDistributionFailure("1|999|*", 3, "Illegal distribution spec \"1|999|*\". Copy counts must be in the range 1-255.");
    }

    private void setNodes(Group g, String nodes) {
        StringTokenizer st = new StringTokenizer(nodes, ",");
        List<ConfiguredNode> nodeList = new ArrayList<>();
        while (st.hasMoreTokens()) {
            nodeList.add(new ConfiguredNode(Integer.parseInt(st.nextToken()), false));
        }
        g.setNodes(nodeList);
    }

    private void verifyUniqueHashes(Group g, Set<Integer> hashes) {
        assertFalse(g.getName(), hashes.contains(g.getDistributionHash()));
        hashes.add(g.getDistributionHash());
    }

    private Group buildGroupTree() throws ParseException {
        Group root = new Group(5, "myroot", new Group.Distribution("2|*", 7));
        List<Group> level_one = new ArrayList<Group>();
        level_one.add(new Group(0, "br0", new Group.Distribution("1|1|*", 7)));
        level_one.add(new Group(1, "br1", new Group.Distribution("*", 7)));
        level_one.add(new Group(3, "br3", new Group.Distribution("8|*", 7)));
        level_one.add(new Group(4, "br4", new Group.Distribution("1|*", 7)));
        level_one.add(new Group(5, "br5", new Group.Distribution("*|*|*", 7)));
        level_one.add(new Group(6, "br6", new Group.Distribution("*|*|*|*|*|*", 7)));
        level_one.add(new Group(7, "br7", new Group.Distribution("*", 7)));
        level_one.add(new Group(9, "br9", new Group.Distribution("1|*", 7)));
        for(Group g : level_one) {
            root.addSubGroup(g);
            for (int i=0; i<5; ++i) {
                Group child =  new Group(i, g.getName() + "." + i);
                g.addSubGroup(child);
                List<ConfiguredNode> nodeList = new ArrayList<>();
                for (int j=0; j<5; ++j) nodeList.add(new ConfiguredNode(g.getIndex() * 10 + j, false));
                child.setNodes(nodeList);
            }
        }
            // Create some irregularities
        setNodes(level_one.get(3).getSubgroups().get(2), "19,7,8,17");
        try{
            Group br8 = new Group(5, "br8", new Group.Distribution("*", 5));
            root.addSubGroup(br8);
            assertTrue(false); // Should fail index 5 is in use at that level
        } catch (Exception e) {
            assertEquals("A subgroup with index 5 already exist.", e.getMessage());
        }
        try{
            Group br8 = new Group(5, "br8");
            Group br9 = new Group(2, "br9");
            br8.addSubGroup(br9);
            assertTrue(false); // Should fail as we want distribution to be set on non-leaf node
        } catch (Exception e) {
            assertEquals("Cannot add sub groups to a node without distribution set.", e.getMessage());
        }
        try{
            Group br8 = new Group(5, "br8", new Group.Distribution("*", 5));
            setNodes(br8, "1,2,3");
            assertTrue(false); // Should fail as we can't have distribution on leaf node.
        } catch (Exception e) {
            assertEquals("Cannot add nodes to non-leaf group with distribution set", e.getMessage());
        }
        root.calculateDistributionHashValues();
        Set<Integer> distributionHashes = new HashSet<Integer>();
        verifyUniqueHashes(root, distributionHashes);
        return root;
    }

    @Test
    public void testNormalusage() throws ParseException {
        Group root = new Group(2, "myroot", new Group.Distribution("*", 2));
        assertFalse(root.isLeafGroup());

        Group branch = new Group(5, "myleaf");
        assertTrue(branch.isLeafGroup());

        root = buildGroupTree();

        String expected = "Group(name: myroot, index: 5, distribution: 2|*, subgroups: 8) {\n"
                        + "  Group(name: br0, index: 0, distribution: 1|1|*, subgroups: 5) {\n"
                        + "    Group(name: br0.0, index: 0, nodes( 0 1 2 3 4 )) {\n"
                        + "    }\n"
                        + "    Group(name: br0.1, index: 1, nodes( 0 1 2 3 4 )) {\n"
                        + "    }\n"
                        + "    Group(name: br0.2, index: 2, nodes( 0 1 2 3 4 )) {\n"
                        + "    }\n"
                        + "    Group(name: br0.3, index: 3, nodes( 0 1 2 3 4 )) {\n"
                        + "    }\n"
                        + "    Group(name: br0.4, index: 4, nodes( 0 1 2 3 4 )) {\n"
                        + "    }\n"
                        + "  }\n";
        assertEquals(expected, root.toString().substring(0, expected.length()));

        assertEquals("br5.br5.0", root.getGroupForNode(52).getPath());
    }

    private Group.Distribution dummyDistribution() throws Exception {
        return new Group.Distribution("*", 1);
    }

    @Test
    public void testRootGroupDoesNotIncludeGroupNameWhenNoChildren() {
        Group g = new Group(0, "donkeykong");
        assertThat(g.getUnixStylePath(), is("/"));
    }

    @Test
    public void testChildNamesDoNotIncludeRootGroupName() throws Exception {
        Group g = new Group(0, "donkeykong", dummyDistribution());
        Group child = new Group(1, "mario");
        g.addSubGroup(child);
        assertThat(child.getUnixStylePath(), is("/mario"));
    }

    @Test
    public void testNestedGroupsAreSlashSeparated() throws Exception {
        Group g = new Group(0, "donkeykong", dummyDistribution());
        Group mario = new Group(1, "mario", dummyDistribution());
        Group toad = new Group(2, "toad");
        mario.addSubGroup(toad);
        g.addSubGroup(mario);

        assertThat(toad.getUnixStylePath(), is("/mario/toad"));
    }

    @Test
    public void testMultipleLeafGroupsAreEnumerated() throws Exception {
        Group g = new Group(0, "donkeykong", dummyDistribution());
        Group mario = new Group(1, "mario", dummyDistribution());
        Group toad = new Group(2, "toad");
        mario.addSubGroup(toad);
        Group yoshi = new Group(3, "yoshi");
        mario.addSubGroup(yoshi);
        g.addSubGroup(mario);
        Group luigi = new Group(4, "luigi");
        g.addSubGroup(luigi);

        assertThat(toad.getUnixStylePath(), is("/mario/toad"));
        assertThat(yoshi.getUnixStylePath(), is("/mario/yoshi"));
        assertThat(luigi.getUnixStylePath(), is("/luigi"));
    }

}
