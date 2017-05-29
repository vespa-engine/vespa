// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.ApplicationNotReadyException;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.DeactivatedContainer;
import com.yahoo.jdisc.application.GuiceRepository;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.application.OsgiHeader;
import com.yahoo.jdisc.service.ContainerNotReadyException;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.statistics.ActiveContainerStatistics;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ApplicationLoader implements BootstrapLoader, ContainerActivator, CurrentContainer {

    private static final Logger log = Logger.getLogger(ApplicationLoader.class.getName());

    private final OsgiFramework osgiFramework;
    private final GuiceRepository guiceModules = new GuiceRepository();
    private final AtomicReference<ActiveContainer> containerRef = new AtomicReference<>();
    private final Object appLock = new Object();
    private final List<Bundle> appBundles = new ArrayList<>();
    private final ActiveContainerStatistics statistics = new ActiveContainerStatistics();
    private Application application;
    private ApplicationInUseTracker applicationInUseTracker;

    public ApplicationLoader(OsgiFramework osgiFramework, Iterable<? extends Module> guiceModules) {
        this.osgiFramework = osgiFramework;
        this.guiceModules.install(new ApplicationEnvironmentModule(this));
        this.guiceModules.installAll(guiceModules);
    }

    @Override
    public ContainerBuilder newContainerBuilder() {
        return new ContainerBuilder(guiceModules);
    }

    @Override
    public DeactivatedContainer activateContainer(ContainerBuilder builder) {
        ActiveContainer next = builder != null ? new ActiveContainer(builder) : null;
        final ActiveContainer prev;
        synchronized (appLock) {
            if (application == null && next != null) {
                next.release();
                throw new ApplicationNotReadyException();
            }

            if (next != null) {
                next.retainReference(applicationInUseTracker);
            }

            prev = containerRef.getAndSet(next);
            statistics.onActivated(next);
            if (prev == null) {
                return null;
            }
            statistics.onDeactivated(prev);
        }
        prev.release();
        DeactivatedContainer deactivatedContainer = prev.shutdown();

        final WeakReference<ActiveContainer> prevContainerReference = new WeakReference<>(prev);
        final Runnable deactivationMonitor = () -> {
            long waitTimeSeconds = 30L;
            long totalTimeWaited = 0L;

            while (!Thread.interrupted()) {
                final long currentWaitTimeSeconds = waitTimeSeconds;
                totalTimeWaited += currentWaitTimeSeconds;

                Interruption.mapExceptionToThreadState(() ->
                                Thread.sleep(TimeUnit.MILLISECONDS.convert(currentWaitTimeSeconds, TimeUnit.SECONDS))
                );

                statistics.printSummaryToLog();
                final ActiveContainer prevContainer = prevContainerReference.get();
                if (prevContainer == null || prevContainer.retainCount() == 0) {
                    return;
                }
                log.warning("Previous container not terminated in the last " + totalTimeWaited + " seconds."
                        + " Reference state={ " + prevContainer.currentState() + " }");

                waitTimeSeconds = (long) (waitTimeSeconds * 1.2);
            }
            log.warning("Deactivation monitor thread unexpectedly interrupted");
        };
        final Thread deactivationMonitorThread = new Thread(deactivationMonitor, "Container deactivation monitor");
        deactivationMonitorThread.setDaemon(true);
        deactivationMonitorThread.start();

        return deactivatedContainer;
    }

    @Override
    public ContainerSnapshot newReference(URI uri) {
        ActiveContainer container = containerRef.get();
        if (container == null) {
            throw new ContainerNotReadyException();
        }
        return container.newReference(uri);
    }

    @Override
    public void init(String appLocation, boolean privileged) throws Exception {
        log.finer("Initializing application loader.");
        osgiFramework.start();
        BundleContext ctx = osgiFramework.bundleContext();
        if (ctx != null) {
            ctx.registerService(CurrentContainer.class.getName(), this, null);
        }
        if(appLocation == null) {
            return; // application class bound by another module
        }
        try {
            final Class<Application> appClass = ContainerBuilder.safeClassCast(Application.class, Class.forName(appLocation));
            guiceModules.install(new AbstractModule()  {
                @Override
                public void configure() {
                    bind(Application.class).to(appClass);
                }
            });
            return; // application class found on class path
        } catch (ClassNotFoundException e) {
            // location is not a class name
            if (log.isLoggable(Level.FINE)) {
                log.fine("App location is not a class name. Installing bundle");
            }
        }
        appBundles.addAll(osgiFramework.installBundle(appLocation));
        if (OsgiHeader.isSet(appBundles.get(0), OsgiHeader.PRIVILEGED_ACTIVATOR)) {
            osgiFramework.startBundles(appBundles, privileged);
        }

    }

    @Override
    public void start() throws Exception {
        log.finer("Initializing application.");
        Injector injector = guiceModules.activate();
        Application app;
        if (!appBundles.isEmpty()) {
            Bundle appBundle = appBundles.get(0);
            if (!OsgiHeader.isSet(appBundle, OsgiHeader.PRIVILEGED_ACTIVATOR)) {
                osgiFramework.startBundles(appBundles, false);
            }
            List<String> header = OsgiHeader.asList(appBundle, OsgiHeader.APPLICATION);
            if (header.size() != 1) {
                throw new IllegalArgumentException("OSGi header '" + OsgiHeader.APPLICATION + "' has " + header.size() +
                                                   " entries, expected 1.");
            }
            String appName = header.get(0);
            log.finer("Loading application class " + appName + " from bundle '" + appBundle.getSymbolicName() + "'.");
            Class<Application> appClass = ContainerBuilder.safeClassCast(Application.class,
                                                                         appBundle.loadClass(appName));
            app = injector.getInstance(appClass);
        } else {
            app = injector.getInstance(Application.class);
            log.finer("Injecting instance of " + app.getClass().getName() + ".");
        }
        try {
            synchronized (appLock) {
                application = app;
                applicationInUseTracker = new ApplicationInUseTracker();
            }
            app.start();
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception thrown while activating application.", e);
            synchronized (appLock) {
                application = null;
                applicationInUseTracker = null;
            }
            app.destroy();
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        log.finer("Destroying application.");
        Application app;
        ApplicationInUseTracker applicationInUseTracker;
        synchronized (appLock) {
            app = application;
            applicationInUseTracker = this.applicationInUseTracker;
        }
        if (app == null || applicationInUseTracker == null) {
            return;
        }
        try {
            app.stop();
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception thrown while deactivating application.", e);
        }
        synchronized (appLock) {
            application = null;
        }
        activateContainer(null);
        synchronized (appLock) {
            this.applicationInUseTracker = null;
        }
        applicationInUseTracker.release();
        applicationInUseTracker.applicationInUseLatch.await();
        app.destroy();
    }

    @Override
    public void destroy() {
        log.finer("Destroying application loader.");
        try {
            osgiFramework.stop();
        } catch (BundleException e) {
            e.printStackTrace();
        }
    }

    public Application application() {
        synchronized (appLock) {
            return application;
        }
    }

    public ActiveContainerStatistics getActiveContainerStatistics() {
        return statistics;
    }

    public OsgiFramework osgiFramework() {
        return osgiFramework;
    }

    private static class ApplicationInUseTracker extends AbstractResource {
        //opened when the application has been stopped and there's no active containers
        final CountDownLatch applicationInUseLatch = new CountDownLatch(1);

        @Override
        protected void destroy() {
            applicationInUseLatch.countDown();
        }
    }

    private static class Interruption {
        interface Runnable_throws<E extends Throwable>  {
            void run() throws E;
        }

        public static void mapExceptionToThreadState(Runnable_throws<InterruptedException> runnable) {
            try {
                runnable.run();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
