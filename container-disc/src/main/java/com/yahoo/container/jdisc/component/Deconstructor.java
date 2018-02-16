// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.SharedResource;

import java.util.Random;
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
        if (component instanceof AbstractComponent) {
            AbstractComponent abstractComponent = (AbstractComponent) component;
            if (abstractComponent.isDeconstructable()) {
                executor.schedule(new DestructComponentTask(abstractComponent), delay, TimeUnit.SECONDS);
            }
        } else if (component instanceof Provider) {
            log.info("Starting deconstruction of " + component);
            ((Provider)component).deconstruct();
            log.info("Finished deconstructing " + component);
        } else if (component instanceof SharedResource) {
            // No need to delay release, as jdisc does ref-counting
            log.info("Starting deconstruction of " + component);
            ((SharedResource)component).release();
            log.info("Finished deconstructing " + component);
        }
    }

    private static class DestructComponentTask implements Runnable {
        private final AbstractComponent component;

        DestructComponentTask(AbstractComponent component) {
            this.component = component;
        }

        public void run() {
            log.info("Starting deconstruction of " + component);
            try {
                component.deconstruct();
                log.info("Finished deconstructing " + component);
            } catch (Error e) {
                try {
                    Thread.sleep((long) (new Random(System.nanoTime()).nextDouble() * 180 * 1000));
                } catch (InterruptedException exception) { }
                com.yahoo.protect.Process.logAndDie("Error when deconstructing " + component, e);
            } catch (Exception e) {
                log.log(WARNING, "Exception thrown when deconstructing " + component, e);
            } catch (Throwable t) {
                log.log(WARNING, "Unexpected Throwable thrown when deconstructing " + component, t);
            }
        }
    }
}
