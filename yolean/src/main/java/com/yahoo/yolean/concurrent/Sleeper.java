// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import com.yahoo.yolean.UncheckedInterruptedException;

import java.time.Duration;

import static com.yahoo.yolean.Exceptions.uncheck;

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

    Sleeper DEFAULT = Thread::sleep;
    Sleeper NOOP = millis -> {};

}
