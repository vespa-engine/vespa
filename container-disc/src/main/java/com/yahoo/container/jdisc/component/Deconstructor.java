// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.log.LogLevel;

import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
* @author tonyv
* @author gv
*/
public class Deconstructor implements ComponentDeconstructor {

    private static final Logger log = Logger.getLogger(Deconstructor.class.getName());

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getThreadFactory("deconstructor"));

    private final int delay;

    public Deconstructor(boolean delayDeconstruction) {
        delay = delayDeconstruction ? 60 : 0;
    }


    @Override
    public void deconstruct(Object component) {
        // The mix of deconstructing some components on separate thread and some on caller thread could in theory violate
        // ordering contraints between components.
        if (component instanceof AbstractComponent) {
            AbstractComponent abstractComponent = (AbstractComponent) component;
            if (abstractComponent.isDeconstructable()) {
                executor.schedule(new DestructComponentTask(abstractComponent), delay, TimeUnit.SECONDS);
            }
        } else if (component instanceof Provider) {
            // TODO Providers should most likely be deconstructed similarily to AbstractComponent
            log.info("Starting deconstruction of provider " + component);
            ((Provider)component).deconstruct();
            log.info("Finished deconstructing of provider " + component);
        } else if (component instanceof SharedResource) {
            log.info("Releasing container reference to resource " + component);
            // No need to delay release, as jdisc does ref-counting
            ((SharedResource)component).release();
        }
    }

    private static class DestructComponentTask implements Runnable {

        private final AbstractComponent component;

        DestructComponentTask(AbstractComponent component) {
            this.component = component;
        }

        /** Returns a random value which will be different across identical containers invoking this at the same time */
        private long random() {
            return new SecureRandom().nextLong();
        }

        public void run() {
            log.info("Starting deconstruction of component " + component);
            try {
                component.deconstruct();
                log.info("Finished deconstructing of component " + component);
            }
            catch (Exception | NoClassDefFoundError e) { // May get class not found due to it being already unloaded
                log.log(WARNING, "Exception thrown when deconstructing component " + component, e);
            }
            catch (Error e) {
                try {
                    // Randomize restart over 10 minutes to avoid simultaneous cluster restarts
                    long randomSleepSeconds = random() * 60 * 10;
                    log.log(LogLevel.FATAL, "Error when deconstructing component " + component + ". Will sleep for " +
                                            randomSleepSeconds + " seconds then restart", e);
                    Thread.sleep(randomSleepSeconds * 1000);
                }
                catch (InterruptedException exception) {
                    log.log(WARNING, "Randomized wait before dying disrupted. Dying now.");
                }
                com.yahoo.protect.Process.logAndDie("Shutting down due to error when deconstructing component " + component);
            }
            catch (Throwable e) {
                log.log(WARNING, "Non-error not exception throwable thrown when deconstructing component  " + component, e);
            }
        }
    }

}
