// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.server;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class JsonReaderTest {
    @Test
    public void testParsingOfTimeout() throws InvalidContentException {
        assertEquals(Optional.empty(), JsonReader.parseTimeout(null));
        assertEquals(Optional.of(Duration.ofMillis(12500)), JsonReader.parseTimeout("12.5"));
        assertEquals(Optional.of(Duration.ofMillis(0)), JsonReader.parseTimeout("-1"));
        assertEquals(Optional.of(Duration.ofMillis(0)), JsonReader.parseTimeout("0.0001"));
    }
}