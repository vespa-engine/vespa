// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vespa.clustercontroller.core.NodeEvent;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class EventTypeIs extends BaseMatcher<NodeEvent> {
    private final NodeEvent.Type expected;

    private EventTypeIs(NodeEvent.Type expected) {
        this.expected = expected;
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof NodeEvent)) {
            return false;
        }
        return expected.equals(((NodeEvent)o).getType());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("NodeEvent with description '%s'", expected));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        NodeEvent other = (NodeEvent)item;
        description.appendText(String.format("got description '%s'", other.getDescription()));
    }

    public static EventTypeIs eventTypeIs(NodeEvent.Type type) {
        return new EventTypeIs(type);
    }
}
