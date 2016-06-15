// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerThread;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.http.test.ClientTestDriver;
import org.testng.annotations.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.testng.AssertJUnit.assertTrue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class ClientThreadingTestCase extends AbstractClientTestCase {

    @Test(enabled = false)
    public void requireThatDefaultThreadFactoryCreatesContainerThreads() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance(new HttpClientConfig.Builder());
        ThreadAwareDispatch dispatch = new ThreadAwareDispatch(driver, "/foo.html");
        assertDispatch(dispatch);
        assertTrue(dispatch.latch.await(60, TimeUnit.SECONDS));
        assertTrue(dispatch.thread instanceof ContainerThread);
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatThreadFactoryIsUsed() throws Exception {
        final MyThreadFactory factory = new MyThreadFactory();
        ClientTestDriver driver = ClientTestDriver.newInstance(new AbstractModule() {

            @Override
            protected void configure() {
                bind(ThreadFactory.class).toInstance(factory);
            }
        });
        ThreadAwareDispatch dispatch = new ThreadAwareDispatch(driver, "/foo.html");
        assertDispatch(dispatch);
        assertTrue(dispatch.latch.await(60, TimeUnit.SECONDS));
        assertTrue(factory.threads.contains(dispatch.thread));
        assertTrue(driver.close());
    }

    private static void assertDispatch(ThreadAwareDispatch dispatch) throws Exception {
        dispatch.dispatch();
        assertRequest(dispatch.driver.server(),
                      expectedRequestChunks("POST " + dispatch.requestUri + " HTTP/1.1\r\n" +
                                            "Host: .+\r\n" +
                                            "Connection: keep-alive\r\n" +
                                            "Accept: .+/.+\r\n" +
                                            "User-Agent: JDisc/1.0\r\n" +
                                            "\r\n"),
                      responseChunks("HTTP/1.1 200 OK\r\n" +
                                     "Content-Type: text/plain; charset=UTF-8\r\n" +
                                     "\r\n"),
                      dispatch);
        assertResponse(dispatch.get(60, TimeUnit.SECONDS),
                       expectedResponseStatus(200),
                       expectedResponseMessage("OK"),
                       expectedResponseHeaders());
    }

    private static class ThreadAwareDispatch extends RequestDispatch {

        final CountDownLatch latch = new CountDownLatch(1);
        final ClientTestDriver driver;
        final String requestUri;
        Thread thread;

        ThreadAwareDispatch(ClientTestDriver driver, String requestUri) {
            this.driver = driver;
            this.requestUri = requestUri;
        }

        @Override
        protected Request newRequest() {
            return new Request(driver.currentContainer(), driver.server().newRequestUri(requestUri));
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            thread = Thread.currentThread();
            latch.countDown();
            return null;
        }
    }

    private static class MyThreadFactory implements ThreadFactory {

        final BlockingQueue<Thread> threads = new LinkedBlockingQueue<>();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task);
            threads.add(thread);
            return thread;
        }
    }
}
