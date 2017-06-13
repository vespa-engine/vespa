// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.protect;

/**
 * @author bratseth
 */
public class TestErrorMessage extends junit.framework.TestCase {

    public void testErrorMessages() {
        ErrorMessage m1=new ErrorMessage(17,"Message");
        ErrorMessage m2=new ErrorMessage(17,"Message","Detail");
        ErrorMessage m3=new ErrorMessage(17,"Message","Detail",new Exception("Throwable message"));
        assertEquals(17,m1.getCode());
        assertEquals("Message",m1.getMessage());
        assertEquals("Detail",m2.getDetailedMessage());
        assertEquals("Throwable message",m3.getCause().getMessage());
        assertEquals("error : Message (Detail: Throwable message)",m3.toString());
    }

    public void testErrorMessageEquality() {
        assertEquals(new ErrorMessage(17,"Message"),new ErrorMessage(17,"Message"));
        assertFalse(new ErrorMessage(16,"Message").equals(new ErrorMessage(17,"Message")));
        assertFalse(new ErrorMessage(17,"Message").equals(new ErrorMessage(17,"Other message")));
        assertFalse(new ErrorMessage(17,"Message").equals(new ErrorMessage(17,"Message","Detail")));
        assertFalse(new ErrorMessage(17,"Message","Detail").equals(new ErrorMessage(17,"Message")));
        assertEquals(new ErrorMessage(17,"Message","Detail"),new ErrorMessage(17,"Message","Detail",new Exception()));
        assertTrue(new ErrorMessage(17,"Message","Detail").equals(new ErrorMessage(17,"Message","Detail")));
        assertFalse(new ErrorMessage(17,"Message","Detail").equals(new ErrorMessage(17,"Message","Other detail")));
    }

}
