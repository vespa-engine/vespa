// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class RequestTestCase {

    @Test
    void requireThatAccessorsWork() throws BindingSetNotFoundException {
        MyTimer timer = new MyTimer();
        timer.currentTime = 69;

        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(timer);
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create("http://foo/bar"));
        assertNotNull(request);
        assertEquals(URI.create("http://foo/bar"), request.getUri());
        assertTrue(request.isServerRequest());
        assertEquals(69, request.creationTime(TimeUnit.MILLISECONDS));
        assertNull(request.getTimeout(TimeUnit.MILLISECONDS));
        request.setTimeout(10, TimeUnit.MILLISECONDS);
        assertNotNull(request.getTimeout(TimeUnit.MILLISECONDS));
        assertEquals(10, request.timeRemaining(TimeUnit.MILLISECONDS).longValue());
        assertTrue(request.context().isEmpty());
        assertTrue(request.headers().isEmpty());
        TimeoutManager timeoutManager = new MyTimeoutManager();
        request.setTimeoutManager(timeoutManager);
        assertSame(timeoutManager, request.getTimeoutManager());
        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatCancelWorks() {
        MyTimer timer = new MyTimer();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(timer);
        Request request = newRequest(driver);
        assertFalse(request.isCancelled());
        request.cancel();
        assertTrue(request.isCancelled());
        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatDefaultTimeoutIsInfinite() {
        MyTimer timer = new MyTimer();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(timer);
        Request request = newRequest(driver);
        assertNull(request.getTimeout(TimeUnit.MILLISECONDS));
        assertNull(request.timeRemaining(TimeUnit.MILLISECONDS));
        assertFalse(request.isCancelled());
        timer.currentTime = Long.MAX_VALUE;
        assertFalse(request.isCancelled());
        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatTimeRemainingUsesTimer() {
        MyTimer timer = new MyTimer();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(timer);
        Request request = newRequest(driver);
        request.setTimeout(10, TimeUnit.MILLISECONDS);
        for (timer.currentTime = 0; timer.currentTime <= request.getTimeout(TimeUnit.MILLISECONDS);
             ++timer.currentTime)
        {
            assertEquals(request.getTimeout(TimeUnit.MILLISECONDS) - timer.currentTime,
                    request.timeRemaining(TimeUnit.MILLISECONDS).longValue());
        }
        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatTimeoutCausesCancel() {
        MyTimer timer = new MyTimer();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(timer);
        Request request = newRequest(driver);
        request.setTimeout(10, TimeUnit.MILLISECONDS);
        assertFalse(request.isCancelled());
        timer.currentTime = 10;
        assertTrue(request.isCancelled());
        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatCancelIsTrueIfParentIsCancelled() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        Request parent = newRequest(driver);
        Request child = new Request(parent, URI.create("http://localhost/"));
        parent.cancel();
        assertTrue(child.isCancelled());
        parent.release();
        child.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatDestroyReleasesContainer() {
        final MyContainer container = new MyContainer();
        Request request = new Request(new CurrentContainer() {

            @Override
            public Container newReference(URI uri) {
                return container;
            }
        }, URI.create("http://localhost/"));
        assertEquals(1, container.refCount);
        request.release();
        assertEquals(0, container.refCount);
    }

    @Test
    void requireThatServerConnectResolvesToServerBinding() {
        MyContainer container = new MyContainer();
        Request request = new Request(container, URI.create("http://localhost/"));
        request.connect(new MyResponseHandler());
        assertNotNull(container.asServer);
        assertTrue(container.asServer);
    }

    @Test
    void requireThatClientConnectResolvesToClientBinding() {
        MyContainer container = new MyContainer();
        Request serverReq = new Request(container, URI.create("http://localhost/"));
        Request clientReq = new Request(serverReq, URI.create("http://localhost/"));
        clientReq.connect(new MyResponseHandler());
        assertNotNull(container.asServer);
        assertFalse(container.asServer);
    }

    @Test
    void requireThatNullTimeoutManagerThrowsException() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        Request request = newRequest(driver);

        try {
            request.setTimeoutManager(null);
            fail();
        } catch (NullPointerException e) {

        }

        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatTimeoutManagerCanNotBeReplaced() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        Request request = newRequest(driver);

        TimeoutManager manager = new MyTimeoutManager();
        request.setTimeoutManager(manager);
        try {
            request.setTimeoutManager(manager);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Timeout manager already set.", e.getMessage());
        }

        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatSetTimeoutCallsTimeoutManager() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        Request request = newRequest(driver);

        MyTimeoutManager timeoutManager = new MyTimeoutManager();
        request.setTimeoutManager(timeoutManager);
        request.setTimeout(6, TimeUnit.SECONDS);
        assertEquals(6000, timeoutManager.timeoutMillis);

        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatSetTimeoutManagerPropagatesCurrentTimeout() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        Request request = newRequest(driver);

        MyTimeoutManager timeoutManager = new MyTimeoutManager();
        request.setTimeout(6, TimeUnit.SECONDS);
        request.setTimeoutManager(timeoutManager);
        assertEquals(6000, timeoutManager.timeoutMillis);

        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatUriIsNormalized() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());

        assertUri(driver, "http://host/foo", "http://host/foo");
        assertUri(driver, "http://host/./foo", "http://host/foo");
        assertUri(driver, "http://host/././foo", "http://host/foo");
        assertUri(driver, "http://host/foo/", "http://host/foo/");
        assertUri(driver, "http://host/foo/.", "http://host/foo/");
        assertUri(driver, "http://host/foo/./", "http://host/foo/");
        assertUri(driver, "http://host/foo/./.", "http://host/foo/");
        assertUri(driver, "http://host/foo/././", "http://host/foo/");
        assertUri(driver, "http://host/foo/..", "http://host/");
        assertUri(driver, "http://host/foo/../", "http://host/");
        assertUri(driver, "http://host/foo/../bar", "http://host/bar");
        assertUri(driver, "http://host/foo/../bar/", "http://host/bar/");
        assertUri(driver, "http://host//foo//", "http://host/foo/");
        assertUri(driver, "http://host///foo///", "http://host/foo/");
        assertUri(driver, "http://host///foo///bar///", "http://host/foo/bar/");

        assertTrue(driver.close());
    }

    private static void assertUri(CurrentContainer container, String requestUri, String expectedUri) {
        Request serverReq = new Request(container, URI.create(requestUri));
        assertEquals(expectedUri, serverReq.getUri().toString());

        Request clientReq = new Request(serverReq, URI.create(requestUri));
        assertEquals(expectedUri, clientReq.getUri().toString());

        serverReq.release();
        clientReq.release();
    }

    private static Request newRequest(TestDriver driver) {
        driver.activateContainer(driver.newContainerBuilder());
        return new Request(driver, URI.create("http://host/path"));
    }

    private static class MyTimer extends AbstractModule implements Timer {

        long currentTime = 0;

        @Override
        public long currentTimeMillis() {
            return currentTime;
        }

        @Override
        protected void configure() {
            bind(Timer.class).toInstance(this);
        }
    }

    private static class MyContainer implements CurrentContainer, Container {

        Boolean asServer = null;
        int refCount = 1;

        @Override
        public Container newReference(URI uri) {
            return this;
        }

        @Override
        public RequestHandler resolveHandler(Request request) {
            this.asServer = request.isServerRequest();
            RequestHandler requestHandler = new MyRequestHandler();
            UriPattern pattern = new UriPattern("http://*/*");
            request.setBindingMatch(new BindingMatch<>(pattern.match(request.getUri()),
                                                       requestHandler,
                                                       pattern));
            return requestHandler;
        }

        @Override
        public <T> T getInstance(Class<T> type) {
            return Guice.createInjector().getInstance(type);
        }

        @Override
        public ResourceReference refer() {
            ++refCount;
            return new ResourceReference() {
                @Override
                public void close() {
                    --refCount;
                }
            };
        }

        @Override
        public void release() {
            --refCount;
        }

        @Override
        public long currentTimeMillis() {
            return 0;
        }
    }

    private static class MyRequestHandler extends NoopSharedResource implements RequestHandler {

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            return new MyContentChannel();
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler handler) {

        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(Response response) {
            return null;
        }
    }

    private static class MyContentChannel implements ContentChannel {

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {

        }

        @Override
        public void close(CompletionHandler handler) {

        }
    }

    private static class MyTimeoutManager implements TimeoutManager {

        long timeoutMillis;

        @Override
        public void scheduleTimeout(Request request) {
            timeoutMillis = request.getTimeout(TimeUnit.MILLISECONDS);
        }
    }
}
