// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.schema.document;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class HnswIndexParamsTestCase {

    @Test
    void override_from() throws Exception {
        var empty = new HnswIndexParams();
        var builder = new HnswIndexParams.Builder();
        builder.setMaxLinksPerNode(7);
        builder.setMultiThreadedIndexing(false);
        var one = builder.build();
        builder.setNeighborsToExploreAtInsert(42);
        var three = builder.build();
        builder.setMaxLinksPerNode(17);
        builder.setNeighborsToExploreAtInsert(500);
        builder.setMultiThreadedIndexing(true);
        var four = builder.build();

        assertThat(empty.maxLinksPerNode(), is(16));
        assertThat(empty.neighborsToExploreAtInsert(), is(200));
        assertThat(empty.multiThreadedIndexing(), is(true));

        assertThat(one.maxLinksPerNode(), is(7));
        assertThat(one.multiThreadedIndexing(), is(false));
        assertThat(three.neighborsToExploreAtInsert(), is(42));

        assertThat(four.maxLinksPerNode(), is(17));
        assertThat(four.neighborsToExploreAtInsert(), is(500));
        assertThat(four.multiThreadedIndexing(), is(true));

        var five = four.overrideFrom(Optional.of(empty));
        assertThat(five.maxLinksPerNode(), is(17));
        assertThat(five.neighborsToExploreAtInsert(), is(500));
        assertThat(five.multiThreadedIndexing(), is(true));

        var six = four.overrideFrom(Optional.of(one));
        assertThat(six.maxLinksPerNode(), is(7));
        assertThat(six.neighborsToExploreAtInsert(), is(500));
        // This is explicitly set to false in 'one'
        assertThat(six.multiThreadedIndexing(), is(false));
    }

}
