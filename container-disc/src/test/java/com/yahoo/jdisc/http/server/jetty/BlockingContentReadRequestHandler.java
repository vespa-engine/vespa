// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.util.concurrent.CountDownLatch;

/**
 * Blocks a dedicated thread reading the request content, mimicking handlers that consume content through a
 * blocking input stream. The thread is only released when the content channel is closed or fails, so
 * {@link #contentReadTerminated} verifies that a failed request read wakes up a blocked handler thread.
 *
 * @author glebashnik
 */
class BlockingContentReadRequestHandler extends AbstractRequestHandler {

    final CountDownLatch contentReadTerminated = new CountDownLatch(1);

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        var content = new ReadableContentChannel();
        var reader = new Thread(() -> {
            try (var in = content.toStream()) {
                in.readAllBytes();
            } catch (Throwable ignored) {
            } finally {
                contentReadTerminated.countDown();
            }
        });
        reader.setDaemon(true);
        reader.start();
        return content;
    }
}
