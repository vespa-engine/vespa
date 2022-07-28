// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class NullContentTestCase {

    @Test
    void requireThatWriteThrowsException() {
        CompletionHandler completion = Mockito.mock(CompletionHandler.class);
        try {
            NullContent.INSTANCE.write(ByteBuffer.allocate(69), completion);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        Mockito.verifyNoInteractions(completion);
    }

    @Test
    void requireThatWriteEmptyDoesNotThrowException() {
        CompletionHandler completion = Mockito.mock(CompletionHandler.class);
        NullContent.INSTANCE.write(ByteBuffer.allocate(0), completion);
        Mockito.verify(completion).completed();
        Mockito.verifyNoMoreInteractions(completion);
    }

    @Test
    void requireThatCloseCallsCompletion() {
        CompletionHandler completion = Mockito.mock(CompletionHandler.class);
        NullContent.INSTANCE.close(completion);
        Mockito.verify(completion).completed();
        Mockito.verifyNoMoreInteractions(completion);
    }

    @Test
    void requireThatCloseWithoutCompletionDoesNotThrow() {
        NullContent.INSTANCE.close(null);
    }
}
