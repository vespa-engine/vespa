// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.handler.CompletionHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingCompletionHandlerTestCase {

    @Test
    void requireThatCompletedThrowsException() {
        CompletionHandler completion = new NonWorkingCompletionHandler();
        try {
            completion.completed();
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatFailedThrowsException() {
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
