// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.document;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static com.yahoo.searchdefinition.document.HnswIndexParams.DistanceMetric;

public class HnswIndexParamsTestCase {

    @Test
    public void override_from() throws Exception {
        var empty = new HnswIndexParams();
        var builder = new HnswIndexParams.Builder();
        builder.setMaxLinksPerNode(7);
        var one = builder.build();
        builder.setDistanceMetric("angular");
        var two = builder.build();
        builder.setNeighborsToExploreAtInsert(42);
        var three = builder.build();
        builder.setMaxLinksPerNode(17);
        builder.setDistanceMetric("geodegrees");
        builder.setNeighborsToExploreAtInsert(500);
        var four = builder.build();

        assertThat(empty.maxLinksPerNode(), is(16));
        assertThat(empty.distanceMetric(), is(DistanceMetric.EUCLIDEAN));
        assertThat(empty.neighborsToExploreAtInsert(), is(200));

        assertThat(one.maxLinksPerNode(), is(7));
        assertThat(two.distanceMetric(), is(DistanceMetric.ANGULAR));
        assertThat(three.neighborsToExploreAtInsert(), is(42));

        assertThat(four.maxLinksPerNode(), is(17));
        assertThat(four.distanceMetric(), is(DistanceMetric.GEODEGREES));
        assertThat(four.neighborsToExploreAtInsert(), is(500));

        var five = four.overrideFrom(empty);
        assertThat(five.maxLinksPerNode(), is(17));
        assertThat(five.distanceMetric(), is(DistanceMetric.GEODEGREES));
        assertThat(five.neighborsToExploreAtInsert(), is(500));

        var six = four.overrideFrom(two);
        assertThat(six.maxLinksPerNode(), is(7));
        assertThat(six.distanceMetric(), is(DistanceMetric.ANGULAR));
        assertThat(six.neighborsToExploreAtInsert(), is(500));
    }

}
