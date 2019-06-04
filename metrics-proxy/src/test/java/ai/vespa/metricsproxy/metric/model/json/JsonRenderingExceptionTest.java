/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class JsonRenderingExceptionTest {

    @Test
    public void error_message_is_wrapped_in_json_object() {
        var exception =  new JsonRenderingException("bad");
        assertEquals("{\"error\":\"bad\"}", exception.getMessageAsJson());
    }

    @Test
    public void quotes_are_escaped() {
        var exception =  new JsonRenderingException("Message \" with \" embedded quotes.");
        assertEquals("{\"error\":\"Message \\\" with \\\" embedded quotes.\"}", exception.getMessageAsJson());
    }
}
