// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vespa.clustercontroller.core.Event;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class EventTimeIs extends BaseMatcher<Event> {
    private final long expected;

    private EventTimeIs(long expected) {
        this.expected = expected;
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof Event)) {
            return false;
        }
        return expected == ((Event)o).getTimeMs();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("Event with time %d", expected));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        Event other = (Event)item;
        description.appendText(String.format("event time is %d", other.getTimeMs()));
    }

    public static EventTimeIs eventTimeIs(long time) {
        return new EventTimeIs(time);
    }
}

