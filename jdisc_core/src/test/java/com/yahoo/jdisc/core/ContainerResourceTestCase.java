// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ContainerResourceTestCase {

    @Test
    void requireThatBoundRequestHandlersAreRetainedOnActivate() {
        MyRequestHandler foo = new MyRequestHandler();
        MyRequestHandler bar = new MyRequestHandler();

        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings("foo").bind("http://foo/", foo);
        builder.serverBindings("bar").bind("http://bar/", bar);
        assertEquals(0, foo.retainCnt.get());
        assertEquals(0, bar.retainCnt.get());

        driver.activateContainer(builder);
        assertEquals(1, foo.retainCnt.get());
        assertEquals(1, bar.retainCnt.get());
        assertTrue(driver.close());
    }

    @Test
    void requireThatBoundRequestHandlersAreReleasedOnTermination() {
        MyRequestHandler handler = new MyRequestHandler();

        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://localhost/", handler);
        driver.activateContainer(builder);

        Container container = driver.newReference(URI.create("http://localhost/"));
        assertEquals(1, handler.retainCnt.get());
        driver.activateContainer(null);
        assertEquals(1, handler.retainCnt.get());
        container.release();
        assertEquals(0, handler.retainCnt.get());

        assertTrue(driver.close());
    }

    @Test
    void requireThatServerProvidersAreRetainedOnActivate() {
        MyServerProvider foo = new MyServerProvider();
        MyServerProvider bar = new MyServerProvider();

        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverProviders().install(foo);
        builder.serverProviders().install(bar);
        assertEquals(0, foo.retainCnt.get());
        assertEquals(0, bar.retainCnt.get());

        driver.activateContainer(builder);
        assertEquals(1, foo.retainCnt.get());
        assertEquals(1, bar.retainCnt.get());
        assertTrue(driver.close());
    }

    @Test
    void requireThatServerProvidersAreReleasedOnTermination() {
        MyServerProvider server = new MyServerProvider();

        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverProviders().install(server);
        driver.activateContainer(builder);

        Container container = driver.newReference(URI.create("http://localhost/"));
        assertEquals(1, server.retainCnt.get());
        driver.activateContainer(null);
        assertEquals(1, server.retainCnt.get());
        container.release();
        assertEquals(0, server.retainCnt.get());

        assertTrue(driver.close());
    }

    private static class MyRequestHandler implements RequestHandler {

        final AtomicInteger retainCnt = new AtomicInteger(0);

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceReference refer() {
            retainCnt.incrementAndGet();
            return new ResourceReference() {
                @Override
                public void close() {
                    retainCnt.decrementAndGet();
                }
            };
        }

        @Override
        public void release() {
            retainCnt.decrementAndGet();
        }
    }

    private static class MyServerProvider implements ServerProvider {

        final AtomicInteger retainCnt = new AtomicInteger(0);

        @Override
        public void start() {

        }

        @Override
        public void close() {

        }

        @Override
        public ResourceReference refer() {
            retainCnt.incrementAndGet();
            return new ResourceReference() {
                @Override
                public void close() {
                    retainCnt.decrementAndGet();
                }
            };
        }

        @Override
        public void release() {
            retainCnt.decrementAndGet();
        }
    }
}
