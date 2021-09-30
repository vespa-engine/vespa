// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vespa.clustercontroller.core.NodeEvent;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;

public class NodeEventWithDescription extends BaseMatcher<NodeEvent> {
    private final String expected;

    private NodeEventWithDescription(String expected) {
        this.expected = expected;
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof NodeEvent)) {
            return false;
        }
        return expected.equals(((NodeEvent) o).getDescription());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("NodeEvent with description '%s'", expected));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        if (!(item instanceof NodeEvent)) {
            return;
        }
        NodeEvent other = (NodeEvent)item;
        description.appendText(String.format("got description '%s'", other.getDescription()));
    }

    @Factory
    public static NodeEventWithDescription nodeEventWithDescription(String description) {
        return new NodeEventWithDescription(description);
    }
}
