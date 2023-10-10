// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.Response;
import com.yahoo.messagebus.Reply;
import com.yahoo.text.Utf8String;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class MbusResponseTestCase {

    @Test
    public void requireThatAccessorsWork() {
        MyReply reply = new MyReply();
        MbusResponse response = new MbusResponse(Response.Status.OK, reply);
        assertSame(reply, response.getReply());
    }

    @Test
    public void requireThatReplyCanNotBeNull() {
        try {
            new MbusResponse(Response.Status.OK, null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    private class MyReply extends Reply {

        @Override
        public Utf8String getProtocol() {
            return null;
        }

        @Override
        public int getType() {
            return 0;
        }
    }
}
