// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.NodeEvent;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class EventForNode extends BaseMatcher<NodeEvent> {
    private final Node expected;

    private EventForNode(Node expected) {
        this.expected = expected;
    }

    @Override
    public boolean matches(Object o) {
        return ((NodeEvent)o).getNode().getNode().equals(expected);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("NodeEvent for node %s", expected));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        NodeEvent other = (NodeEvent)item;
        description.appendText(String.format("got node %s", other.getNode().getNode()));
    }

    public static EventForNode eventForNode(Node expected) {
        return new EventForNode(expected);
    }
}
