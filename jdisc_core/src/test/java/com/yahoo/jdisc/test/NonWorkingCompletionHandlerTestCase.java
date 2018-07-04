// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.handler.CompletionHandler;
import org.junit.Test;

import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingCompletionHandlerTestCase {

    @Test
    public void requireThatCompletedThrowsException() {
        CompletionHandler completion = new NonWorkingCompletionHandler();
        try {
            completion.completed();
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatFailedThrowsException() {
        CompletionHandler completion = new NonWorkingCompletionHandler();
        try {
            completion.failed(null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        completion = new NonWorkingCompletionHandler();
        try {
            completion.failed(new Throwable());
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }
}
