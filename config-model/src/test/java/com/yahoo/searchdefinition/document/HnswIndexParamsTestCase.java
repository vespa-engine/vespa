// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.document;

import java.util.Optional;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class HnswIndexParamsTestCase {

    @Test
    public void override_from() throws Exception {
        var empty = new HnswIndexParams();
        var builder = new HnswIndexParams.Builder();
        builder.setMaxLinksPerNode(7);
        var one = builder.build();
        builder.setNeighborsToExploreAtInsert(42);
        var three = builder.build();
        builder.setMaxLinksPerNode(17);
        builder.setNeighborsToExploreAtInsert(500);
        var four = builder.build();

        assertThat(empty.maxLinksPerNode(), is(16));
        assertThat(empty.neighborsToExploreAtInsert(), is(200));

        assertThat(one.maxLinksPerNode(), is(7));
        assertThat(three.neighborsToExploreAtInsert(), is(42));

        assertThat(four.maxLinksPerNode(), is(17));
        assertThat(four.neighborsToExploreAtInsert(), is(500));

        var five = four.overrideFrom(Optional.of(empty));
        assertThat(five.maxLinksPerNode(), is(17));
        assertThat(five.neighborsToExploreAtInsert(), is(500));

        var six = four.overrideFrom(Optional.of(one));
        assertThat(six.maxLinksPerNode(), is(7));
        assertThat(six.neighborsToExploreAtInsert(), is(500));
    }

}
