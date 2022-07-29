// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.DeactivatedContainer;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ContainerTerminationTestCase {

    @Test
    void requireThatAccessorsWork() {
        Object obj = new Object();
        ContainerTermination termination = new ContainerTermination(obj);
        assertSame(obj, termination.appContext());
    }

    @Test
    void requireThatAppContextIsFromBuilder() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        Object obj = new Object();
        builder.setAppContext(obj);
        driver.activateContainer(builder);
        DeactivatedContainer container = driver.activateContainer(null);
        assertSame(obj, container.appContext());
        assertTrue(driver.close());
    }

    @Test
    void requireThatEarlyTerminationIsNotified() {
        ContainerTermination termination = new ContainerTermination(null);
        termination.run();
        MyTask task = new MyTask();
        termination.notifyTermination(task);
        assertTrue(task.done);
    }

    @Test
    void requireThatLaterTerminationIsNotified() {
        ContainerTermination termination = new ContainerTermination(null);
        MyTask task = new MyTask();
        termination.notifyTermination(task);
        assertFalse(task.done);
        termination.run();
        assertTrue(task.done);
    }

    @Test
    void requireThatNotifyCanOnlyBeCalledOnce() {
        ContainerTermination termination = new ContainerTermination(null);
        termination.notifyTermination(new MyTask());
        try {
            termination.notifyTermination(new MyTask());
        } catch (IllegalStateException e) {

        }
    }

    private static class MyTask implements Runnable {

        boolean done = false;

        @Override
        public void run() {
            done = true;
        }
    }
}
