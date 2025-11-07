// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class FastContentOutputStreamTestCase {

    @Test
    void requireThatNullContentChannelIsAccepted() throws Exception {
        FastContentOutputStream out = new FastContentOutputStream((ContentChannel) null);

        // write operations should be no-ops
        out.write(42);
        out.write(new byte[]{1, 2, 3});
        out.write(new byte[]{1, 2, 3}, 0, 2);
        out.flush();

        // close should complete immediately
        out.close();
        out.get();
    }

    @Test
    void requireThatAllMethodsDelegateToWriter() throws Exception {
        FastContentWriter writer = Mockito.mock(FastContentWriter.class);
        FastContentOutputStream out = new FastContentOutputStream(writer);

        out.write(new byte[]{6, 9});
        out.flush();
        Mockito.verify(writer).write(Mockito.any(ByteBuffer.class));

        out.close();
        Mockito.verify(writer).close();

        out.cancel(true);
        Mockito.verify(writer).cancel(true);
        out.cancel(false);
        Mockito.verify(writer).cancel(false);

        out.isCancelled();
        Mockito.verify(writer).isCancelled();

        out.isDone();
        Mockito.verify(writer).isDone();

        out.get();
        Mockito.verify(writer).get();

        out.get(600, TimeUnit.SECONDS);
        Mockito.verify(writer).get(600, TimeUnit.SECONDS);

        Runnable listener = Mockito.mock(Runnable.class);
        Executor executor = Mockito.mock(Executor.class);
        out.addListener(listener, executor);
        Mockito.verify(writer).addListener(listener, executor);
    }
}
