// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.Group;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LeafGroupsTest {

    @Test
    void rootGroupCountedAsLeafWhenNoChildren() {
        Group g = new Group(0, "donkeykong");

        List<Group> leaves = LeafGroups.enumerateFrom(g);
        assertThat(leaves.size(), is(1));
        assertThat(leaves.get(0).getName(), is("donkeykong"));
    }

    private Group.Distribution dummyDistribution() throws Exception {
        return new Group.Distribution("*", 1);
    }

    @Test
    void singleLeafIsEnumerated() throws Exception {
        Group g = new Group(0, "donkeykong", dummyDistribution());
        Group child = new Group(1, "mario");
        g.addSubGroup(child);

        List<Group> leaves = LeafGroups.enumerateFrom(g);
        assertThat(leaves.size(), is(1));
        assertThat(leaves.get(0).getName(), is("mario"));
    }

    @Test
    void singleLeafIsEnumeratedInNestedCase() throws Exception {
        Group g = new Group(0, "donkeykong", dummyDistribution());
        Group child = new Group(1, "mario", dummyDistribution());
        child.addSubGroup(new Group(2, "toad"));
        g.addSubGroup(child);

        List<Group> leaves = LeafGroups.enumerateFrom(g);
        assertThat(leaves.size(), is(1));
        assertThat(leaves.get(0).getName(), is("toad"));
    }

    @Test
    void multipleLeafGroupsAreEnumerated() throws Exception {
        Group g = new Group(0, "donkeykong", dummyDistribution());
        Group child = new Group(1, "mario", dummyDistribution());
        child.addSubGroup(new Group(2, "toad"));
        child.addSubGroup(new Group(3, "yoshi"));
        g.addSubGroup(child);
        g.addSubGroup(new Group(4, "luigi"));

        List<Group> leaves = LeafGroups.enumerateFrom(g);
        // Ensure that output order matches insertion order.
        leaves.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        assertThat(leaves.size(), is(3));
        assertThat(leaves.get(0).getName(), is("toad"));
        assertThat(leaves.get(1).getName(), is("yoshi"));
        assertThat(leaves.get(2).getName(), is("luigi"));
    }
}
