// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.ApplicationNotReadyException;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.NonWorkingOsgiFramework;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ApplicationLoaderTestCase {

    @Test
    void requireThatStartFailsWithoutApplication() throws Exception {
        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(),
                Collections.<Module>emptyList());
        try {
            loader.init(null, false);
            loader.start();
            fail();
        } catch (ConfigurationException e) {

        }
    }

    @Test
    void requireThatStopDoesNotFailWithoutStart() throws Exception {
        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(),
                Collections.<Module>emptyList());
        loader.stop();
        loader.destroy();
    }

    @Test
    void requireThatDestroyDoesNotFailWithActiveContainer() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        assertNull(driver.activateContainer(driver.newContainerBuilder()));
        assertTrue(driver.close());
    }

    @Test
    void requireThatApplicationStartExceptionUnsetsAndDestroysApplication() throws Exception {
        MyApplication app = MyApplication.newStartException();
        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(),
                Arrays.asList(new MyApplicationModule(app)));
        loader.init(null, false);
        try {
            loader.start();
            fail();
        } catch (MyException e) {

        }
        assertNull(loader.application());
        assertFalse(app.stop.await(100, TimeUnit.MILLISECONDS));
        assertTrue(app.destroy.await(600, TimeUnit.SECONDS));
        try {
            loader.activateContainer(loader.newContainerBuilder());
            fail();
        } catch (ApplicationNotReadyException e) {

        }
        loader.stop();
        loader.destroy();
    }

    @Test
    void requireThatApplicationStopExceptionDestroysApplication() throws Exception {
        MyApplication app = MyApplication.newStopException();
        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(),
                Arrays.asList(new MyApplicationModule(app)));
        loader.init(null, false);
        loader.start();
        try {
            loader.stop();
        } catch (MyException e) {

        }
        assertTrue(app.destroy.await(600, TimeUnit.SECONDS));
        loader.destroy();
    }

    @Test
    void requireThatApplicationDestroyIsCalledAfterContainerTermination() throws InterruptedException {
        MyApplication app = MyApplication.newInstance();
        TestDriver driver = TestDriver.newInjectedApplicationInstance(app);
        ContainerBuilder builder = driver.newContainerBuilder();
        MyRequestHandler requestHandler = new MyRequestHandler();
        builder.serverBindings().bind("scheme://host/path", requestHandler);
        driver.activateContainer(builder);
        driver.dispatchRequest("scheme://host/path", new MyResponseHandler());
        driver.scheduleClose();
        assertFalse(app.destroy.await(100, TimeUnit.MILLISECONDS));
        requestHandler.responseHandler.handleResponse(new Response(Response.Status.OK)).close(null);
        assertTrue(app.destroy.await(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatContainerActivatorReturnsPrev() throws Exception {
        TestDriver driver = TestDriver.newInjectedApplicationInstance(MyApplication.newInstance());
        assertNull(driver.activateContainer(driver.newContainerBuilder()));
        assertNotNull(driver.activateContainer(null));
        assertTrue(driver.close());
    }

    @Test
    void requireThatOsgiServicesAreRegistered() {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        BundleContext ctx = driver.osgiFramework().bundleContext();
        Object service = ctx.getService(ctx.getServiceReference(CurrentContainer.class.getName()));
        assertTrue(service instanceof CurrentContainer);
        assertTrue(driver.close());
    }

    @Test
    void requireThatThreadFactoryCanBeBound() {
        final ThreadFactory factory = Executors.defaultThreadFactory();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(ThreadFactory.class).toInstance(factory);
            }
        });
        ContainerBuilder builder = driver.newContainerBuilder();
        assertSame(factory, builder.getInstance(ThreadFactory.class));
        assertTrue(driver.close());
    }

    private static class MyApplicationModule extends AbstractModule {

        final Application application;

        public MyApplicationModule(Application application) {
            this.application = application;
        }

        @Override
        protected void configure() {
            bind(Application.class).toInstance(application);
        }
    }

    private static class MyApplication implements Application {

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(1);
        final CountDownLatch destroy = new CountDownLatch(1);
        final boolean startException;
        final boolean stopException;

        MyApplication(boolean startException, boolean stopException) {
            this.startException = startException;
            this.stopException = stopException;
        }

        @Override
        public void start() {
            start.countDown();
            if (startException) {
                throw new MyException();
            }
        }

        @Override
        public void stop() {
            stop.countDown();
            if (stopException) {
                throw new MyException();
            }
        }

        @Override
        public void destroy() {
            destroy.countDown();
        }

        public static MyApplication newInstance() {
            return new MyApplication(false, false);
        }

        public static MyApplication newStartException() {
            return new MyApplication(true, false);
        }

        public static MyApplication newStopException() {
            return new MyApplication(false, true);
        }
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        ResponseHandler responseHandler;

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            responseHandler = handler;
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
            if (handler != null) {
                handler.completed();
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            if (handler != null) {
                handler.completed();
            }
        }
    }

    private static class MyException extends RuntimeException {

    }
}
