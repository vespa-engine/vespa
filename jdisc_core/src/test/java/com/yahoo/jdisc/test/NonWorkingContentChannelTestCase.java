// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingContentChannelTestCase {

    @Test
    public void requireThatWriteThrowsException() {
        ContentChannel content = new NonWorkingContentChannel();
        try {
            content.write(null, null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        content = new NonWorkingContentChannel();
        try {
            content.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        content = new NonWorkingContentChannel();
        try {
            content.write(ByteBuffer.allocate(69), new MyCompletion());
            fail();
        } catch (UnsupportedOperationException e) {

        }
        content = new NonWorkingContentChannel();
        try {
            content.write(null, new MyCompletion());
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatCloseThrowsException() {
        ContentChannel content = new NonWorkingContentChannel();
        try {
            content.close(null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        content = new NonWorkingContentChannel();
        try {
            content.close(new MyCompletion());
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    private static class MyCompletion implements CompletionHandler {

        @Override
        public void completed() {

        }

        @Override
        public void failed(Throwable t) {

        }
    }
}
