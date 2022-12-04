// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableMap;
import com.yahoo.vespa.hosted.provision.node.History.Event;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class HistoryTest {

    @Test
    public void truncate_log() {
        assertEquals(0, new History(List.of(), List.of()).log().size());
        assertEquals(1, new History(ImmutableMap.of(), shuffledEvents(1), 2).log().size());
        assertEquals(2, new History(ImmutableMap.of(), shuffledEvents(2), 2).log().size());

        History history = new History(ImmutableMap.of(), shuffledEvents(5), 3);
        assertEquals(3, history.log().size());
        assertEquals("Most recent events are kept",
                     List.of(2L, 3L, 4L),
                     history.log().stream().map(e -> e.at().toEpochMilli()).collect(Collectors.toList()));
    }

    private static List<Event> shuffledEvents(int count) {
        Instant start = Instant.ofEpochMilli(0);
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new Event(Event.Type.values()[i], Agent.system, start.plusMillis(i)));
        }
        Collections.shuffle(events);
        return events;
    }

}
