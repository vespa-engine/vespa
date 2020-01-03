// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class ErrorResponseTest {

    @Test
    public void error_message_is_wrapped_in_json_object() {
        var json =  ErrorResponse.asErrorJson("bad");
        assertEquals("{\"error\":\"bad\"}", json);
    }

    @Test
    public void quotes_are_escaped() {
        var json =  ErrorResponse.asErrorJson("Message \" with \" embedded quotes.");
        assertEquals("{\"error\":\"Message \\\" with \\\" embedded quotes.\"}", json);
    }

}
