// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EventLogTest {
    private final MetricUpdater metricUpdater = mock(MetricUpdater.class);
    private final NodeEvent nodeEvent = mock(NodeEvent.class);

    private EventLog eventLog;

    private void initialize(MetricUpdater updater) {
        eventLog = new EventLog(new FakeTimer(), updater);

        // Avoid NullPointerException...
        NodeInfo nodeInfo = mock(NodeInfo.class);
        when(nodeEvent.getNode()).thenReturn(nodeInfo);
        Node node = mock(Node.class);
        when(nodeInfo.getNode()).thenReturn(node);
    }

    @Test
    void testMetric() {
        initialize(metricUpdater);

        eventLog.addNodeOnlyEvent(nodeEvent, Level.INFO);

        verify(metricUpdater).recordNewNodeEvent();
        verifyNoMoreInteractions(metricUpdater);
    }

    @Test
    void testNoEventsDoNotThrowException() {
        initialize(metricUpdater);
        StringBuilder builder = new StringBuilder();
        Node nonExistantNode = new Node(NodeType.DISTRIBUTOR, 0);
        eventLog.writeHtmlState(builder, nonExistantNode);
        assertNotEquals("", builder.toString());
    }
}
