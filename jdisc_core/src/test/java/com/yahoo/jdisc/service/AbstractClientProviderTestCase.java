// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class AbstractClientProviderTestCase {

    @Test
    void requireThatAbstractClassIsAClientProvider() {
        assertTrue(ClientProvider.class.isInstance(new MyClientProvider()));
    }

    @Test
    void requireThatStartDoesNotThrowAnException() {
        new MyClientProvider().start();
    }

    private static class MyClientProvider extends AbstractClientProvider {

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            return null;
        }
    }
}
