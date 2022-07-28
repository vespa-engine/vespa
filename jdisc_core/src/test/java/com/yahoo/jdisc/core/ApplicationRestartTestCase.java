// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.NonWorkingOsgiFramework;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ApplicationRestartTestCase {

    @Test
    void requireThatStopStartDoesNotBreakShutdown() throws Exception {
        ApplicationLoader loader = newApplicationLoader();
        loader.init(null, false);
        loader.start();
        assertGracefulStop(loader);
        loader.start();
        assertGracefulStop(loader);
        loader.destroy();
    }

    @Test
    void requireThatDestroyInitDoesNotBreakShutdown() throws Exception {
        ApplicationLoader loader = newApplicationLoader();
        loader.init(null, false);
        loader.start();
        assertGracefulStop(loader);
        loader.destroy();
        loader.init(null, false);
        loader.start();
        assertGracefulStop(loader);
        loader.destroy();
    }

    private static ApplicationLoader newApplicationLoader() {
        return new ApplicationLoader(new NonWorkingOsgiFramework(),
                                     Arrays.asList(new AbstractModule() {
                                         @Override
                                         public void configure() {
                                             bind(Application.class).to(SimpleApplication.class);
                                         }
                                     }));
    }

    private static void assertGracefulStop(ApplicationLoader loader) throws Exception {
        MyRequestHandler requestHandler = new MyRequestHandler();
        ContainerBuilder builder = loader.newContainerBuilder();
        builder.serverBindings().bind("http://host/path", requestHandler);
        loader.activateContainer(builder);

        MyResponseHandler responseHandler = new MyResponseHandler();
        Request request = new Request(loader, URI.create("http://host/path"));
        request.connect(responseHandler).close(null);
        request.release();

        StopTask task = new StopTask(loader);
        task.start();
        assertFalse(task.latch.await(100, TimeUnit.MILLISECONDS));
        requestHandler.responseHandler.handleResponse(new Response(Response.Status.OK)).close(null);
        assertTrue(task.latch.await(600, TimeUnit.SECONDS));
    }

    private static class StopTask extends Thread {

        final ApplicationLoader loader;
        final CountDownLatch latch = new CountDownLatch(1);

        StopTask(ApplicationLoader loader) {
            this.loader = loader;
        }

        @Override
        public void run() {
            try {
                loader.stop();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            latch.countDown();
        }
    }

    private static class SimpleApplication implements Application {

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void destroy() {

        }
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        ResponseHandler responseHandler;

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            this.responseHandler = handler;
            return new MyContentChannel();
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(Response response) {
            return new MyContentChannel();
        }
    }

    private static class MyContentChannel implements ContentChannel {

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            handler.completed();
        }

        @Override
        public void close(CompletionHandler handler) {
            handler.completed();
        }
    }
}
