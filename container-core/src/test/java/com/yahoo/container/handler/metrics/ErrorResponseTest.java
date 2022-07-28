// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class ErrorResponseTest {

    @Test
    void error_message_is_wrapped_in_json_object() {
        var json =  ErrorResponse.asErrorJson("bad");
        assertEquals("{\"error\":\"bad\"}", json);
    }

    @Test
    void quotes_are_escaped() {
        var json =  ErrorResponse.asErrorJson("Message \" with \" embedded quotes.");
        assertEquals("{\"error\":\"Message \\\" with \\\" embedded quotes.\"}", json);
    }

}
