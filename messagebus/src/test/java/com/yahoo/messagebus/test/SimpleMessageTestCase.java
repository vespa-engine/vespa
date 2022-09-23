// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleMessageTestCase {

    @Test
    void requireThatAccessorsWork() {
        SimpleMessage msg = new SimpleMessage("foo");
        assertEquals(SimpleProtocol.MESSAGE, msg.getType());
        assertEquals(SimpleProtocol.NAME, msg.getProtocol());
        assertEquals(3, msg.getApproxSize());
        assertEquals("foo", msg.getValue());
        msg.setValue("bar");
        assertEquals("bar", msg.getValue());
    }

}
