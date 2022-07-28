// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ContainerSnapshotTestCase {

    @Test
    void requireThatServerHandlerCanBeResolved() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://foo/*", MyRequestHandler.newInstance());
        driver.activateContainer(builder);

        Request request = new Request(driver, URI.create("http://foo/"));
        assertNotNull(request.container().resolveHandler(request));
        assertNotNull(request.getBindingMatch());
        request.release();

        request = new Request(driver, URI.create("http://foo/"), false);
        assertNull(request.container().resolveHandler(request));
        assertNull(request.getBindingMatch());
        request.release();

        request = new Request(driver, URI.create("http://bar/"));
        assertNull(request.container().resolveHandler(request));
        assertNull(request.getBindingMatch());
        request.release();

        request = new Request(driver, URI.create("http://bar/"), false);
        assertNull(request.container().resolveHandler(request));
        assertNull(request.getBindingMatch());
        request.release();

        assertTrue(driver.close());
    }

    @Test
    void requireThatClientHandlerCanBeResolved() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.clientBindings().bind("http://foo/*", MyRequestHandler.newInstance());
        driver.activateContainer(builder);

        Request request = new Request(driver, URI.create("http://foo/"));
        assertNull(request.container().resolveHandler(request));
        assertNull(request.getBindingMatch());
        request.release();

        request = new Request(driver, URI.create("http://foo/"), false);
        assertNotNull(request.container().resolveHandler(request));
        assertNotNull(request.getBindingMatch());
        request.release();

        request = new Request(driver, URI.create("http://bar/"));
        assertNull(request.container().resolveHandler(request));
        assertNull(request.getBindingMatch());
        request.release();

        request = new Request(driver, URI.create("http://bar/"), false);
        assertNull(request.container().resolveHandler(request));
        assertNull(request.getBindingMatch());
        request.release();

        assertTrue(driver.close());
    }

    @Test
    void requireThatClientBindingsAreUsed() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.clientBindings().bind("http://host/path", MyRequestHandler.newInstance());
        driver.activateContainer(builder);
        Request request = new Request(driver, URI.create("http://host/path"), false);
        assertNotNull(request.container().resolveHandler(request));
        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatBindingMatchIsSetByResolveHandler() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://*/*", MyRequestHandler.newInstance());
        driver.activateContainer(builder);

        Request request = new Request(driver, URI.create("http://localhost:69/status.html"));
        assertNotNull(request.container().resolveHandler(request));
        BindingMatch<RequestHandler> match = request.getBindingMatch();
        assertNotNull(match);
        assertEquals(3, match.groupCount());
        assertEquals("localhost", match.group(0));
        assertEquals("69", match.group(1));
        assertEquals("status.html", match.group(2));
        request.release();

        assertTrue(driver.close());
    }

    @Test
    void requireThatNewRequestHasSameSnapshot() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Request foo = new Request(driver, URI.create("http://host/foo"));
        Request bar = new Request(foo, URI.create("http://host/bar"));
        assertSame(foo.container(), bar.container());
        foo.release();
        bar.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatActiveInjectorIsUsed() {
        final Object obj = new Object();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Object.class).toInstance(obj);
            }
        });
        ActiveContainer active = new ActiveContainer(driver.newContainerBuilder());
        ContainerSnapshot snapshot = new ContainerSnapshot(active, null, null, null);
        assertSame(obj, snapshot.getInstance(Object.class));
        snapshot.release();
        assertTrue(driver.close());
    }

    private static class MyContent implements ContentChannel {

        CompletionHandler writeCompletion = null;
        CompletionHandler closeCompletion = null;
        ByteBuffer writeBuf = null;
        boolean closed = false;

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            writeBuf = buf;
            writeCompletion = handler;
        }

        @Override
        public void close(CompletionHandler handler) {
            closed = true;
            closeCompletion = handler;
        }
    }

    private static class MyRequestHandler extends AbstractResource implements RequestHandler {

        final MyContent content = new MyContent();
        Request request = null;
        ResponseHandler handler = null;
        boolean timeout = false;
        boolean destroyed = false;

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            this.request = request;
            this.handler = handler;
            return content;
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler handler) {
            timeout = true;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        static MyRequestHandler newInstance() {
            return new MyRequestHandler();
        }
    }
}
