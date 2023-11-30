// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.DeactivatedContainer;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.core.ApplicationLoader;
import com.yahoo.jdisc.core.BootstrapLoader;
import com.yahoo.jdisc.core.FelixFramework;
import com.yahoo.jdisc.core.FelixParams;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDeniedException;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>This class provides a unified way to set up and run unit tests on jDISC components. In short, it is a programmable
 * {@link BootstrapLoader} that provides convenient access to the {@link ContainerActivator} and {@link
 * CurrentContainer} interfaces. A typical test case using this class looks as follows:</p>
 * <pre>
 *{@literal @}Test
 * public void requireThatMyComponentIsWellBehaved() {
 *     TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
 *     ContainerBuilder builder = driver.newContainerBuilder();
 *     (... configure builder ...)
 *     driver.activateContainer(builder);
 *     (... run tests ...)
 *     assertTrue(driver.close());
 * }
 * </pre>
 * <p>One of the most important things to remember when using this class is to always call {@link #close()} at the end
 * of your test case. This ensures that the tested configuration does not prevent graceful shutdown. If close() returns
 * FALSE, it means that either your components or the test case itself does not conform to the reference counting
 * requirements of {@link Request}, {@link RequestHandler}, {@link ContentChannel}, or {@link CompletionHandler}.</p>
 *
 * @author Simon Thoresen Hult
 */
public class TestDriver implements ContainerActivator, CurrentContainer {

    private static final AtomicInteger testId = new AtomicInteger(0);
    private final FutureTask<Boolean> closeTask = new FutureTask<>(new CloseTask());
    private final ApplicationLoader loader;

    private TestDriver(ApplicationLoader loader) {
        this.loader = loader;
    }

    @Override
    public ContainerBuilder newContainerBuilder() {
        return loader.newContainerBuilder();
    }

    /** Returns the deactivated container, with its container reference already released. */
    @Override
    public DeactivatedContainer activateContainer(ContainerBuilder builder) {
        try (DeactivatedContainer deactivated = loader.activateContainer(builder)) { return deactivated; }
    }

    @Override
    public Container newReference(URI uri) {
        return loader.newReference(uri);
    }

    /**
     * <p>Returns the {@link BootstrapLoader} used by this TestDriver. Use caution when invoking methods on the
     * BootstrapLoader directly, since the lifecycle management done by this TestDriver may become corrupt.</p>
     *
     * @return The BootstrapLoader.
     */
    public BootstrapLoader bootstrapLoader() {
        return loader;
    }

    /**
     * <p>Returns the {@link Application} loaded by this TestDriver. Until {@link #close()} is called, this method will
     * never return null.</p>
     *
     * @return The loaded Application.
     */
    public Application application() {
        return loader.application();
    }

    /**
     * <p>Returns the {@link OsgiFramework} created by this TestDriver. Although this method will never return null, it
     * might return a {@link NonWorkingOsgiFramework} depending on the factory method used to instantiate it.</p>
     *
     * @return The OSGi framework.
     */
    public OsgiFramework osgiFramework() {
        return loader.osgiFramework();
    }

    /**
     * <p>Convenience method to create and {@link Request#connect(ResponseHandler)} a {@link Request} on the {@link
     * CurrentContainer}. This method will either return the corresponding {@link ContentChannel} or throw the
     * appropriate exception (see {@link Request#connect(ResponseHandler)}).</p>
     *
     * @param requestUri      The URI string to parse and pass to the Request constructor.
     * @param responseHandler The ResponseHandler to pass to {@link Request#connect(ResponseHandler)}.
     * @return The ContentChannel returned by {@link Request#connect(ResponseHandler)}.
     * @throws NullPointerException     If the URI string or the {@link ResponseHandler} is null.
     * @throws IllegalArgumentException If the URI string violates RFC&nbsp;2396.
     * @throws BindingNotFoundException If the corresponding call to {@link Container#resolveHandler(Request)}
     *                                  returns null.
     * @throws RequestDeniedException   If the corresponding call to {@link RequestHandler#handleRequest(Request,
     *                                  ResponseHandler)} returns null.
     */
    public ContentChannel connectRequest(String requestUri, ResponseHandler responseHandler) {
        return newRequestDispatch(requestUri, responseHandler).connect();
    }

    /**
     * <p>Convenience method to create a {@link Request}, connect it to a {@link RequestHandler}, and close the returned
     * {@link ContentChannel}. This is the same as calling:</p>
     * <pre>
     * connectRequest(uri, responseHandler).close(null);
     * </pre>
     *
     * @param requestUri      The URI string to parse and pass to the Request constructor.
     * @param responseHandler The ResponseHandler to pass to {@link Request#connect(ResponseHandler)}.
     * @return A waitable Future that provides access to the corresponding {@link Response}.
     * @throws NullPointerException     If the URI string or the {@link ResponseHandler} is null.
     * @throws IllegalArgumentException If the URI string violates RFC&nbsp;2396.
     * @throws BindingNotFoundException If the corresponding call to {@link Container#resolveHandler(Request)}
     *                                  returns null.
     * @throws RequestDeniedException   If the corresponding call to {@link RequestHandler#handleRequest(Request,
     *                                  ResponseHandler)} returns null.
     */
    public Future<Response> dispatchRequest(String requestUri, ResponseHandler responseHandler) {
        return newRequestDispatch(requestUri, responseHandler).dispatch();
    }

    /**
     * <p>Initiates the shut down of this TestDriver in another thread. By doing this in a separate thread, it allows
     * other code to monitor its progress. Unless you need the added monitoring capability, you should use {@link
     * #close()} instead.</p>
     *
     * @see #awaitClose(long, TimeUnit)
     */
    public void scheduleClose() {
        new Thread(closeTask, "TestDriver.Closer").start();
    }

    /**
     * <p>Waits for shut down of this TestDriver to complete. This call must be preceded by a call to {@link
     * #scheduleClose()}.</p>
     *
     * @param timeout The maximum time to wait.
     * @param unit    The time unit of the timeout argument.
     * @return True if shut down completed within the allocated time.
     */
    public boolean awaitClose(long timeout, TimeUnit unit) {
        try {
            closeTask.get(timeout, unit);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e);
        }
    }

    /**
     * <p>Initiatiates shut down of this TestDriver and waits for it to complete. If shut down fails to complete within
     * 60 seconds, this method throws an exception.</p>
     *
     * @return True if shut down completed within the allocated time.
     * @throws IllegalStateException If shut down failed to complete within the allocated time.
     */
    public boolean close() {
        scheduleClose();
        if ( ! awaitClose(600, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Application failed to terminate within allocated time.");
        }
        return true;
    }

    /**
     * <p>Creates a new {@link RequestDispatch} that dispatches a {@link Request} with the given URI and {@link
     * ResponseHandler}.</p>
     *
     * @param requestUri      The uri of the Request to create.
     * @param responseHandler The ResponseHandler to use for the dispather.
     * @return The created RequestDispatch.
     */
    public RequestDispatch newRequestDispatch(final String requestUri, final ResponseHandler responseHandler) {
        return new RequestDispatch() {

            @Override
            protected Request newRequest() {
                return new Request(loader, URI.create(requestUri));
            }

            @Override
            public ContentChannel handleResponse(Response response) {
                return responseHandler.handleResponse(response);
            }
        };
    }

    /**
     * <p>Creates a new TestDriver with an injected {@link Application}.</p>
     *
     * @param appClass     The Application class to inject.
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     */
    public static TestDriver newInjectedApplicationInstance(Class<? extends Application> appClass,
                                                            Module... guiceModules) {
        return newInstance(newOsgiFramework(), null, false,
                           newModuleList(null, appClass, guiceModules));
    }

    /**
     * <p>Creates a new TestDriver with an injected {@link Application}, but without OSGi support.</p>
     *
     * @param appClass     The Application class to inject.
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     * @see #newInjectedApplicationInstance(Class, Module...)
     * @see #newNonWorkingOsgiFramework()
     */
    public static TestDriver newInjectedApplicationInstanceWithoutOsgi(Class<? extends Application> appClass,
                                                                       Module... guiceModules) {
        return newInstance(newNonWorkingOsgiFramework(), null, false,
                           newModuleList(null, appClass, guiceModules));
    }

    /**
     * <p>Creates a new TestDriver with an injected {@link Application}.</p>
     *
     * @param app          The Application to inject.
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     */
    public static TestDriver newInjectedApplicationInstance(Application app, Module... guiceModules) {
        return newInstance(newOsgiFramework(), null, false, newModuleList(app, null, guiceModules));
    }

    /**
     * <p>Creates a new TestDriver with an injected {@link Application}, but without OSGi support.</p>
     *
     * @param app          The Application to inject.
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     * @see #newInjectedApplicationInstance(Application, Module...)
     * @see #newNonWorkingOsgiFramework()
     */
    public static TestDriver newInjectedApplicationInstanceWithoutOsgi(Application app, Module... guiceModules) {
        return newInstance(newNonWorkingOsgiFramework(), null, false, newModuleList(app, null, guiceModules));
    }

    /**
     * <p>Creates a new TestDriver with a predefined {@link Application} implementation. The injected Application class
     * implements nothing but the bare minimum to conform to the Application interface.</p>
     *
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     */
    public static TestDriver newSimpleApplicationInstance(Module... guiceModules) {
        return newInstance(newOsgiFramework(), null, false,
                           newModuleList(null, SimpleApplication.class, guiceModules));
    }

    /**
     * <p>Creates a new TestDriver with a predefined {@link Application} implementation, but without OSGi support. The
     * injected Application class implements nothing but the bare minimum to conform to the Application interface.</p>
     *
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     * @see #newSimpleApplicationInstance(Module...)
     * @see #newNonWorkingOsgiFramework()
     */
    public static TestDriver newSimpleApplicationInstanceWithoutOsgi(Module... guiceModules) {
        return newInstance(newNonWorkingOsgiFramework(), null, false,
                           newModuleList(null, SimpleApplication.class, guiceModules));
    }

    /**
     * <p>Creates a new TestDriver from an application bundle. This runs the same code path as the actual jDISC startup
     * code. Note that the named bundle must have a "X-JDisc-Application" bundle instruction, or setup will fail.</p>
     *
     * @param bundleLocation The location of the application bundle to load.
     * @param privileged     Whether or not privileges should be marked as available to the application bundle.
     * @param guiceModules   The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     */
    public static TestDriver newApplicationBundleInstance(String bundleLocation, boolean privileged,
                                                          Module... guiceModules) {
        return newInstance(newOsgiFramework(), bundleLocation, privileged, Arrays.asList(guiceModules));
    }

    /**
     * <p>Creates a new TestDriver with the given parameters. This is the factory method that all other factory methods
     * call. It allows you to specify all parts of the TestDriver manually.</p>
     *
     * @param osgiFramework  The {@link OsgiFramework} to assign to the created TestDriver.
     * @param bundleLocation The location of the application bundle to load, may be null.
     * @param privileged     Whether or not privileges should be marked as available to the application bundle.
     * @param guiceModules   The Guice {@link Module Modules} to install prior to startup.
     * @return The created TestDriver.
     */
    public static TestDriver newInstance(OsgiFramework osgiFramework, String bundleLocation, boolean privileged,
                                         Module... guiceModules) {
        return newInstance(osgiFramework, bundleLocation, privileged, Arrays.asList(guiceModules));
    }

    /**
     * <p>Factory method to create a working {@link OsgiFramework}. This method is used by all {@link TestDriver}
     * factories that DO NOT have the "WithoutOsgi" suffix.</p>
     *
     * @return A working OsgiFramework.
     */
    public static FelixFramework newOsgiFramework() {
        return new FelixFramework(new FelixParams().setCachePath("target/bundlecache" + testId.getAndIncrement()));
    }

    /**
     * <p>Factory method to create a light-weight {@link OsgiFramework} that throws {@link
     * UnsupportedOperationException} if {@link OsgiFramework#installBundle(String)} or {@link
     * OsgiFramework#startBundles(List, boolean)} is called. This allows for unit testing without the footprint of OSGi
     * support. This method is used by {@link TestDriver} factories that have the "WithoutOsgi" suffix.</p>
     *
     * @return A non-working OsgiFramework.
     */
    public static OsgiFramework newNonWorkingOsgiFramework() {
        return new NonWorkingOsgiFramework();
    }

    private class CloseTask implements Callable<Boolean> {

        @Override
        public Boolean call() throws Exception {
            loader.stop();
            loader.destroy();
            return true;
        }
    }

    private static TestDriver newInstance(OsgiFramework osgiFramework, String bundleLocation, boolean privileged,
                                          Iterable<? extends Module> guiceModules) {
        ApplicationLoader loader = new ApplicationLoader(osgiFramework, guiceModules);
        try {
            loader.init(bundleLocation, privileged);
        } catch (Exception e) {
            throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e);
        }
        try {
            loader.start();
        } catch (Exception e) {
            loader.destroy();
            throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e);
        }
        return new TestDriver(loader);
    }

    private static List<Module> newModuleList(final Application app, final Class<? extends Application> appClass,
                                              Module... guiceModules) {
        List<Module> lst = new LinkedList<>();
        lst.addAll(Arrays.asList(guiceModules));
        lst.add(new AbstractModule() {

            @Override
            public void configure() {
                AnnotatedBindingBuilder<Application> builder = bind(Application.class);
                if (app != null) {
                    builder.toInstance(app);
                } else {
                    builder.to(appClass);
                }
            }
        });
        return lst;
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
}
