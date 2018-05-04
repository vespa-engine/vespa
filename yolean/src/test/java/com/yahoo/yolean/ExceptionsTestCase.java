// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean;

import org.junit.Test;

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

}
