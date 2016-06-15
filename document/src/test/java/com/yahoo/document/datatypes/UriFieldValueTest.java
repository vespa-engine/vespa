// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class UriFieldValueTest {

    @Test
    public void requireThatURICanBeAssigned() {
        UriFieldValue value = new UriFieldValue();
        String uri = "http://user:pass@localhost:69/path#fragment?query";
        value.assign(URI.create(uri));
        assertEquals(uri, value.getWrappedValue());
    }
}
