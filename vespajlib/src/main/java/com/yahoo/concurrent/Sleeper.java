// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.time.Duration;

/**
 * An abstraction used for mocking {@link Thread#sleep(long)} in unit tests.
 *
 * @author bjorncs
 */
public interface Sleeper {
    default void sleep(Duration duration) throws UncheckedInterruptedException {
        uncheck(() -> sleepChecked(duration.toMillis()));
    }

    default void sleepChecked(Duration duration) throws InterruptedException { sleepChecked(duration.toMillis()); }

    default void sleep(long millis) throws UncheckedInterruptedException { uncheck(() -> sleepChecked(millis)); }

    void sleepChecked(long millis) throws InterruptedException;

    Sleeper DEFAULT = new Default();
    Sleeper NOOP = new Noop();

    private void uncheck(InterruptingRunnable runnable) {
        try {
            runnable.run();
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
    }
}

interface InterruptingRunnable { void run() throws InterruptedException; }

class Default implements Sleeper {
    @Override public void sleepChecked(long millis) throws InterruptedException { Thread.sleep(millis); }
}

class Noop implements Sleeper {
    @Override public void sleepChecked(long millis) {}
}
