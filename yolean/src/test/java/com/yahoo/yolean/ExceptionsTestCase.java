// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ExceptionsTestCase {

    @Test
    public void testToMessageStrings() {
        assertEquals("Blah",Exceptions.toMessageString(new Exception("Blah")));
        assertEquals("Blah", Exceptions.toMessageString(new Exception(new Exception("Blah"))));
        assertEquals("Exception",Exceptions.toMessageString(new Exception()));
        assertEquals("Foo: Blah",Exceptions.toMessageString(new Exception("Foo",new Exception(new IllegalArgumentException("Blah")))));
        assertEquals("Foo",Exceptions.toMessageString(new Exception("Foo",new Exception("Foo"))));
        assertEquals("Foo: Exception",Exceptions.toMessageString(new Exception("Foo",new Exception())));
        assertEquals("Foo",Exceptions.toMessageString(new Exception(new Exception("Foo"))));
    }

    @Test
    public void testUnchecks() {
        try {
            Exceptions.uncheck(this::throwIO);
        } catch (UncheckedIOException e) {
            assertEquals("root cause", e.getCause().getMessage());
        }

        try {
            Exceptions.uncheck(this::throwIO, "additional %s", "info");
        } catch (UncheckedIOException e) {
            assertEquals("additional info", e.getMessage());
        }

        try {
            int i = Exceptions.uncheck(this::throwIOWithReturnValue);
        } catch (UncheckedIOException e) {
            assertEquals("root cause", e.getCause().getMessage());
        }

        try {
            int i = Exceptions.uncheck(this::throwIOWithReturnValue, "additional %s", "info");
        } catch (UncheckedIOException e) {
            assertEquals("additional info", e.getMessage());
        }
    }

    private void throwIO() throws IOException {
        throw new IOException("root cause");
    }

    private int throwIOWithReturnValue() throws IOException {
        throw new IOException("root cause");
    }
}
