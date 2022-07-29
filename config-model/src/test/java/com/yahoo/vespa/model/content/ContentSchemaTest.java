// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class ContentSchemaTest {

    @Test
    void requireThatAccessorsWork() {
        ContentSearch search = new ContentSearch.Builder()
                .setQueryTimeout(1.0)
                .setVisibilityDelay(2.0)
                .build();
        assertEquals(1.0, search.getQueryTimeout(), 1E-6);
        assertEquals(2.0, search.getVisibilityDelay(), 1E-6);
    }

    @Test
    void requireThatDefaultsAreNull() {
        ContentSearch search = new ContentSearch.Builder().build();
        assertNull(search.getQueryTimeout());
        assertNull(search.getVisibilityDelay());
    }
}
