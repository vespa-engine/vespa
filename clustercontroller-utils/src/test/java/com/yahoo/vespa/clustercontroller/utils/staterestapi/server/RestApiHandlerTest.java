// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.server;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestApiHandlerTest {
    @Test
    void testParsingOfTimeout() throws InvalidContentException {
        assertEquals(Optional.empty(), RestApiHandler.parseTimeout(null));
        assertEquals(Optional.of(Duration.ofMillis(12500)), RestApiHandler.parseTimeout("12.5"));
        assertEquals(Optional.of(Duration.ofMillis(0)), RestApiHandler.parseTimeout("-1"));
        assertEquals(Optional.of(Duration.ofMillis(0)), RestApiHandler.parseTimeout("0.0001"));
    }
}