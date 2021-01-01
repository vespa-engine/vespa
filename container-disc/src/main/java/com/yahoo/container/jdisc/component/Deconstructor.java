// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Deconstructable;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.SharedResource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private static final Duration RECONFIG_DECONSTRUCT_DELAY = Duration.ofSeconds(60);

    // This must be smaller than the shutdownDeadlineExecutor delay in ConfiguredApplication
    private static final Duration SHUTDOWN_DECONSTRUCT_TIMEOUT = Duration.ofSeconds(45);

    public enum Mode {
        RECONFIG,  // Delay deconstruction to allow old components to finish processing in-flight requests.
        SHUTDOWN   // The container is shutting down. Start deconstructing immediately, and wait until all components
                   // are deconstructed, to prevent shutting down while deconstruct is in progress.
    }

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(2, ThreadFactoryFactory.getThreadFactory("component-deconstructor"));

    private final Mode mode;
    private final Duration delay;

    public Deconstructor(Mode mode) {
        this(mode, (mode == Mode.RECONFIG) ? RECONFIG_DECONSTRUCT_DELAY : Duration.ZERO);
    }

    // For testing only
    Deconstructor(Mode mode, Duration reconfigDeconstructDelay) {
        this.mode = mode;
        this.delay = reconfigDeconstructDelay;
    }

    @Override
    public void deconstruct(List<Object> components, Collection<Bundle> bundles) {
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
                log.log(FINE, () -> "Releasing container reference to resource " + component);
                // No need to delay release, as jdisc does ref-counting
                ((SharedResource) component).release();
            }
        }
        if (!destructibleComponents.isEmpty() || !bundles.isEmpty()) {
            var task = executor.schedule(new DestructComponentTask(destructibleComponents, bundles),
                              delay.getSeconds(), TimeUnit.SECONDS);
            if (mode.equals(Mode.SHUTDOWN)) {
                waitFor(task, SHUTDOWN_DECONSTRUCT_TIMEOUT);
            }
        }
    }

    private void waitFor(ScheduledFuture<?> task, Duration timeout) {
        try {
            log.info("Waiting up to " + timeout.toSeconds() + " seconds for all components to deconstruct.");
            task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.info("Interrupted while waiting for component deconstruction to finish.");
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.warning("Component deconstruction threw an exception: " + e.getMessage());
        } catch (TimeoutException e) {
            log.warning("Component deconstruction timed out.");
        }
    }

    private static class DestructComponentTask implements Runnable {

        private final Random random = new Random(System.nanoTime());
        private final Collection<Deconstructable> components;
        private final Collection<Bundle> bundles;

        DestructComponentTask(Collection<Deconstructable> components, Collection<Bundle> bundles) {
            this.components = components;
            this.bundles = bundles;
        }

        /**
        * Returns a random delay between 0 and 10 minutes which will be different across identical containers invoking this at the same time.
        * Used to randomize restart to avoid simultaneous cluster restarts.
        */
        private Duration getRandomizedShutdownDelay() {
            long seconds = (long) random.nextDouble() * 60 * 10;
            return Duration.ofSeconds(seconds);
        }

        @Override
        public void run() {
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
            // NOTE: It could be tempting to refresh packages here, but if active bundles were using any of
            //       the removed ones, they would switch wiring in the middle of a generation's lifespan.
            //       This would happen if the dependent active bundle has not been rebuilt with a new version
            //       of its dependency(ies).
        }
    }

}
