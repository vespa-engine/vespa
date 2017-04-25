// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.yolean.Exceptions;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
* @author tonyv
* @author gv
*/
public class Deconstructor implements ComponentDeconstructor {
    private static final Logger log = Logger.getLogger(Deconstructor.class.getName());

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("deconstructor"));

    private final int delay;

    public Deconstructor(boolean delayDeconstruction) {
        delay = delayDeconstruction ? 60 : 0;
    }


    @Override
    public void deconstruct(Object component) {
        if (component instanceof AbstractComponent) {
            AbstractComponent abstractComponent = (AbstractComponent) component;
            if (abstractComponent.isDeconstructable()) {
                log.info("Scheduling deconstruction of " + abstractComponent);
                executor.schedule(new DestructComponentTask(abstractComponent), delay, TimeUnit.SECONDS);
            }
        } else if (component instanceof Provider) {
            ((Provider)component).deconstruct();
        } else if (component instanceof SharedResource) {
            // No need to delay release, as jdisc does ref-counting
            ((SharedResource)component).release();
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
            } catch (Exception e) {
                log.warning("Exception thrown when deconstructing " + component + ": " + e.getClass().getName()
                                    + ": " + Exceptions.toMessageString(e));
            }
        }
    }
}
