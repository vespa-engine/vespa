// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.test;

import com.yahoo.processing.Request;
import com.yahoo.processing.request.ErrorMessage;
import org.junit.Test;

/**
 * @author  bratseth
 */
public class ErrorMessageTestCase extends junit.framework.TestCase {

    @Test
    public void testToString() {
        assertEquals("message",new ErrorMessage("message").toString());
        assertEquals("message: hello",new ErrorMessage("message",new Exception("hello")).toString());
        assertEquals("message: detail",new ErrorMessage("message","detail").toString());
        assertEquals("37: message: detail",new ErrorMessage(37,"message","detail").toString());
        assertEquals("message: detail: hello",new ErrorMessage("message","detail",new Exception("hello")).toString());
        assertEquals("message: detail: hello: world",new ErrorMessage("message","detail",new Exception("hello",new Exception("world"))).toString());
        assertEquals("message: detail: hello: Exception",new ErrorMessage("message","detail",new Exception("hello",new Exception())).toString());
        assertEquals("message: detail: hello",new ErrorMessage("message","detail",new Exception(new Exception("hello"))).toString());
        assertEquals("message: detail: java.lang.Exception: Exception",new ErrorMessage("message","detail",new Exception(new Exception())).toString());
    }

    @Test
    public void testAccessors() {
        ErrorMessage m = new ErrorMessage(37,"message","detail",new Exception("hello"));
        assertEquals(37,m.getCode());
        assertEquals("message",m.getMessage());
        assertEquals("detail",m.getDetailedMessage());
        assertEquals("hello",m.getCause().getMessage());
    }

    @Test
    public void testEquality() {
        assertEquals(new ErrorMessage(37,"message","detail",new Exception("hello")),
                     new ErrorMessage(37,"message","detail",new Exception("hello")));
        assertEquals(new ErrorMessage("message","detail",new Exception("hello")),
                     new ErrorMessage("message","detail",new Exception("hello")));
        assertEquals(new ErrorMessage("message",new Exception("hello")),
                     new ErrorMessage("message",new Exception("hello")));
        assertEquals(new ErrorMessage("message"),
                     new ErrorMessage("message"));
        assertEquals(new ErrorMessage("message",new Exception()),
                     new ErrorMessage("message"));
        assertFalse(new ErrorMessage("message").equals(new ErrorMessage("message","detail")));
        assertFalse(new ErrorMessage(37,"message").equals(new ErrorMessage("message")));
        assertFalse(new ErrorMessage(37,"message").equals(new ErrorMessage(38,"message")));
        assertFalse(new ErrorMessage("message","detail1").equals(new ErrorMessage("message","detail2")));
        assertFalse(new ErrorMessage("message1").equals(new ErrorMessage("message2")));
    }

}
