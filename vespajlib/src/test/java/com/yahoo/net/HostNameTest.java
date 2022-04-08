// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HostNameTest {

    @Test
    void testHostnameIsFound() {
        assertFalse(HostName.getLocalhost().isEmpty());
    }

}
