// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import com.yahoo.jdisc.service.CurrentContainer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>This class is a convenient parent class for {@link Application} developers that require simple access to the most
 * commonly used jDISC APIs.</p>
 *
 * <p>A simple hello world application could be implemented like this:</p>
 * <pre>
 * class HelloApplication extends AbstractApplication {
 *
 *     &#64;Inject
 *     public HelloApplication(BundleInstaller bundleInstaller, ContainerActivator activator,
 *                             CurrentContainer container) {
 *         super(bundleInstaller, activator, container);
 *     }
 *
 *     &#64;Override
 *     public void start() {
 *         ContainerBuilder builder = newContainerBuilder();
 *         ServerProvider myServer = new MyHttpServer();
 *         builder.serverProviders().install(myServer);
 *         builder.serverBindings().bind("http://&#42;/&#42;", new MyHelloWorldHandler());
 *
 *         activateContainer(builder);
 *         myServer.start();
 *         myServer.release();
 *     }
 * }
 * </pre>
 *
 * @author Simon Thoresen
 */
public abstract class AbstractApplication implements Application {

    private final CountDownLatch destroyed = new CountDownLatch(1);
    private final BundleInstaller bundleInstaller;
    private final ContainerActivator activator;
    private final CurrentContainer container;

    @Inject
    protected AbstractApplication(BundleInstaller bundleInstaller, ContainerActivator activator,
                                  CurrentContainer container) {
        this.bundleInstaller = bundleInstaller;
        this.activator = activator;
        this.container = container;
    }

    @Override
    public void stop() {

    }

    @Override
    public final void destroy() {
        destroyed.countDown();
    }

    public final List<Bundle> installAndStartBundle(String... locations) throws BundleException {
        return installAndStartBundle(Arrays.asList(locations));
    }

    public final List<Bundle> installAndStartBundle(Iterable<String> locations) throws BundleException {
        return bundleInstaller.installAndStart(locations);
    }

    public final void stopAndUninstallBundle(Bundle... bundles) throws BundleException {
        stopAndUninstallBundle(Arrays.asList(bundles));
    }

    public final void stopAndUninstallBundle(Iterable<Bundle> bundles) throws BundleException {
        bundleInstaller.stopAndUninstall(bundles);
    }

    public final ContainerBuilder newContainerBuilder() {
        return activator.newContainerBuilder();
    }

    public final DeactivatedContainer activateContainer(ContainerBuilder builder) {
        return activator.activateContainer(builder);
    }

    public final CurrentContainer container() {
        return container;
    }

    public final boolean isTerminated() {
        return destroyed.getCount() == 0;
    }

    public final boolean awaitTermination(int timeout, TimeUnit unit) throws InterruptedException {
        return destroyed.await(timeout, unit);
    }

    public final void awaitTermination() throws InterruptedException {
        destroyed.await();
    }
}
