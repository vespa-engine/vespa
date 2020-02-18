// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.log.LogLevel;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

/**
* @author Tony Vaagenes
* @author gv
*/
public class Deconstructor implements ComponentDeconstructor {

    private static final Logger log = Logger.getLogger(Deconstructor.class.getName());

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getThreadFactory("component-deconstructor"));

    private final Duration delay;

    public Deconstructor(boolean delayDeconstruction) {
        this.delay = delayDeconstruction ? Duration.ofSeconds(60) : Duration.ZERO;
    }

    @Override
    public void deconstruct(Collection<Object> components, Collection<Bundle> bundles) {
        Collection<AbstractComponent> destructibleComponents = new ArrayList<>();
        for (var component : components) {
            if (component instanceof AbstractComponent) {
                AbstractComponent abstractComponent = (AbstractComponent) component;
                if (abstractComponent.isDeconstructable()) {
                    destructibleComponents.add(abstractComponent);
                }
            } else if (component instanceof Provider) {
                // TODO Providers should most likely be deconstructed similarly to AbstractComponent
                log.log(LogLevel.DEBUG, () -> "Starting deconstruction of provider " + component);
                ((Provider<?>) component).deconstruct();
                log.log(LogLevel.DEBUG, () -> "Finished deconstruction of provider " + component);
            } else if (component instanceof SharedResource) {
                log.log(LogLevel.DEBUG, () -> "Releasing container reference to resource " + component);
                // No need to delay release, as jdisc does ref-counting
                ((SharedResource) component).release();
            }
        }
        if (! destructibleComponents.isEmpty())
            executor.schedule(new DestructComponentTask(destructibleComponents, bundles),
                              delay.getSeconds(), TimeUnit.SECONDS);
    }

    private static class DestructComponentTask implements Runnable {

        private final Random random = new Random(System.nanoTime());
        private final Collection<AbstractComponent> components;
        private final Collection<Bundle> bundles;

        DestructComponentTask(Collection<AbstractComponent> components, Collection<Bundle> bundles) {
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
                log.log(LogLevel.DEBUG, () -> "Starting deconstruction of component " + component);
                try {
                    component.deconstruct();
                    log.log(LogLevel.DEBUG, () -> "Finished deconstructing of component " + component);
                } catch (Exception | NoClassDefFoundError e) { // May get class not found due to it being already unloaded
                    log.log(WARNING, "Exception thrown when deconstructing component " + component, e);
                } catch (Error e) {
                    try {
                        Duration shutdownDelay = getRandomizedShutdownDelay();
                        log.log(LogLevel.FATAL, "Error when deconstructing component " + component + ". Will sleep for " +
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
                    log.log(LogLevel.DEBUG, () -> "Uninstalling bundle " + bundle);
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
