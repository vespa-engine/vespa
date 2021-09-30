// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vespa.clustercontroller.core.ClusterEvent;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;

public class ClusterEventWithDescription extends BaseMatcher<ClusterEvent> {
    private final String expected;

    private ClusterEventWithDescription(String expected) {
        this.expected = expected;
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof ClusterEvent)) {
            return false;
        }
        return expected.equals(((ClusterEvent) o).getDescription());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("ClusterEvent with description '%s'", expected));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        if (!(item instanceof ClusterEvent)) {
            return;
        }
        ClusterEvent other = (ClusterEvent)item;
        description.appendText(String.format("got description '%s'", other.getDescription()));
    }

    @Factory
    public static ClusterEventWithDescription clusterEventWithDescription(String description) {
        return new ClusterEventWithDescription(description);
    }
}
