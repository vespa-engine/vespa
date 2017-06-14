// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vespa.clustercontroller.core.NodeEvent;
import org.hamcrest.Factory;
import org.mockito.ArgumentMatcher;

public class EventTypeIs extends ArgumentMatcher<NodeEvent> {
    private final NodeEvent.Type expected;

    public EventTypeIs(NodeEvent.Type expected) {
        this.expected = expected;
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof NodeEvent)) {
            return false;
        }
        return expected.equals(((NodeEvent)o).getType());
    }

    @Factory
    public static EventTypeIs eventTypeIs(NodeEvent.Type type) {
        return new EventTypeIs(type);
    }
}
