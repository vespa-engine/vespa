// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import org.junit.Test;

import static ai.vespa.metricsproxy.metric.model.StatusCode.DOWN;
import static ai.vespa.metricsproxy.metric.model.StatusCode.UNKNOWN;
import static ai.vespa.metricsproxy.metric.model.StatusCode.UP;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class StatusCodeTest {

    @Test
    public void strings_are_converted_to_correct_status_code() {
        assertEquals(UP, StatusCode.fromString("up"));
        assertEquals(UP, StatusCode.fromString("UP"));
        assertEquals(UP, StatusCode.fromString("ok"));
        assertEquals(UP, StatusCode.fromString("OK"));

        assertEquals(DOWN, StatusCode.fromString("down"));
        assertEquals(DOWN, StatusCode.fromString("DOWN"));

        assertEquals(UNKNOWN, StatusCode.fromString("unknown"));
        assertEquals(UNKNOWN, StatusCode.fromString("UNKNOWN"));
        assertEquals(UNKNOWN, StatusCode.fromString("anything else is unknown"));
    }

}
