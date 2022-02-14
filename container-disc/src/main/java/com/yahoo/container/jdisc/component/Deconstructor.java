// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Deconstructable;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.yolean.UncheckedInterruptedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

/**
* @author Tony Vaagenes
* @author gv
*/
public class Deconstructor implements ComponentDeconstructor {

    private static final Logger log = Logger.getLogger(Deconstructor.class.getName());

    private final ExecutorService executor =
            Executors.newFixedThreadPool(1, ThreadFactoryFactory.getThreadFactory("component-deconstructor"));

    // This must be smaller than the shutdownDeadlineExecutor delay in ConfiguredApplication
    private final Duration shutdownTimeout;

    public Deconstructor(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public Deconstructor() { this(Duration.ofSeconds(45)); }

    @Override
    public void deconstruct(long generation, List<Object> components, Collection<Bundle> bundles) {
        Collection<Deconstructable> destructibleComponents = new ArrayList<>();
        for (var component : components) {
            if (component instanceof AbstractComponent) {
                AbstractComponent abstractComponent = (AbstractComponent) component;
                if (abstractComponent.isDeconstructable()) {
                    destructibleComponents.add(abstractComponent);
                }
            } else if (component instanceof Provider) {
                destructibleComponents.add((Deconstructable) component);
            } else if (component instanceof SharedResource) {
                // Release shared resources in same order as other components in case of usage without reference counting
                destructibleComponents.add(new SharedResourceReleaser(component));
            }
        }
        if (!destructibleComponents.isEmpty() || !bundles.isEmpty()) {
            executor.execute(new DestructComponentTask(generation, destructibleComponents, bundles));
        }
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            log.info("Waiting up to " + shutdownTimeout.toSeconds() + " seconds for all previous components graphs to deconstruct.");
            if (!executor.awaitTermination(shutdownTimeout.getSeconds(), TimeUnit.SECONDS)) {
                log.warning("Waiting for deconstruction timed out.");
            }
        } catch (InterruptedException e) {
            log.info("Interrupted while waiting for component deconstruction to finish.");
            throw new UncheckedInterruptedException(e, true);
        }
    }

    private static class SharedResourceReleaser implements Deconstructable {
        final SharedResource resource;

        private SharedResourceReleaser(Object resource) { this.resource = (SharedResource) resource; }

        @Override public void deconstruct() { resource.release(); }
    }

    private static class DestructComponentTask implements Runnable {

        private final Random random = new Random(System.nanoTime());
        private final long generation;
        private final Collection<Deconstructable> components;
        private final Collection<Bundle> bundles;

        DestructComponentTask(long generation, Collection<Deconstructable> components, Collection<Bundle> bundles) {
            this.generation = generation;
            this.components = components;
            this.bundles = bundles;
        }

        /**
        * Returns a random delay between 0 and 10 minutes which will be different across identical containers invoking this at the same time.
        * Used to randomize restart to avoid simultaneous cluster restarts.
        */
        private Duration getRandomizedShutdownDelay() {
            long seconds = (long) (random.nextDouble() * 60 * 10);
            return Duration.ofSeconds(seconds);
        }

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            log.info(String.format("Starting deconstruction of %d components and %d bundles from generation %d",
                    components.size(), bundles.size(), generation));
            for (var component : components) {
                log.log(FINE, () -> "Starting deconstruction of " + component);
                try {
                    component.deconstruct();
                    log.log(FINE, () -> "Finished deconstructing of " + component);
                } catch (Exception | NoClassDefFoundError e) { // May get class not found due to it being already unloaded
                    log.log(WARNING, "Exception thrown when deconstructing component " + component, e);
                } catch (Error e) {
                    try {
                        Duration shutdownDelay = getRandomizedShutdownDelay();
                        log.log(Level.SEVERE, "Error when deconstructing component " + component + ". Will sleep for " +
                                shutdownDelay.getSeconds() + " seconds then restart", e);
                        Thread.sleep(shutdownDelay.toMillis());
                    } catch (InterruptedException exception) {
                        log.log(WARNING, "Randomized wait before dying disrupted. Dying now.");
                    }
                    com.yahoo.protect.Process.logAndDie("Shutting down due to error when deconstructing component " + component);
                } catch (Throwable e) {
                    log.log(WARNING, "Non-error not exception throwable thrown when deconstructing component  " + component, e);
                }
            }
            // It should now be safe to uninstall the old bundles.
            for (var bundle : bundles) {
                try {
                    log.log(INFO, "Uninstalling bundle " + bundle);
                    bundle.uninstall();
                } catch (BundleException e) {
                    log.log(SEVERE, "Could not uninstall bundle " + bundle);
                }
            }
            log.info(String.format("Completed deconstruction in %.3f seconds", (System.currentTimeMillis() - start) / 1000D));
            // NOTE: It could be tempting to refresh packages here, but if active bundles were using any of
            //       the removed ones, they would switch wiring in the middle of a generation's lifespan.
            //       This would happen if the dependent active bundle has not been rebuilt with a new version
            //       of its dependency(ies).
        }
    }

}
