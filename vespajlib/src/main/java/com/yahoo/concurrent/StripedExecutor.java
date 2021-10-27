// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executor that serializes runnables with the same key, but may parallelize over different keys.
 *
 * @author jonmv
 */
public class StripedExecutor<Key> {

    private static final Logger logger = Logger.getLogger(StripedExecutor.class.getName());

    private final Map<Key, Deque<Runnable>> commands = new HashMap<>();
    private final ExecutorService executor;

    /** Creates a new StripedExecutor which delegates to a {@link Executors#newCachedThreadPool(ThreadFactory)}. */
    public StripedExecutor() {
        this(Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory(StripedExecutor.class.getSimpleName())));
    }

    /** Creates a new StripedExecutor which delegates to the given executor service. */
    public StripedExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Executes the given command. If other commands are already running or queued for the given key,
     * execution of this command happens after those, on the same thread as is running them.
     * <p>
     * Any exception thrown by the command will only be logged, to allow subsequent commands to run.
     */
    public void execute(Key key, Runnable command) {
        synchronized (commands) {
            if (null == commands.putIfAbsent(key, new ArrayDeque<>(List.of(command))))
                executor.execute(() -> runAll(key));
            else
                commands.get(key).add(command);
        }
    }

    /** Runs all submitted commands for the given key, then removes the queue for the key and returns. */
    private void runAll(Key key) {
        while (true) {
            Runnable command;
            synchronized (commands) {
                command = commands.containsKey(key) ? commands.get(key).poll() : null;
                if (command == null) {
                    commands.remove(key);
                    break;
                }
            }
            try {
                command.run();
            }
            catch (RuntimeException e) {
                logger.log(Level.WARNING, e, () -> "Exception caught: " + Exceptions.toMessageString(e));
            }
        }
    }

    /** Shuts down the delegate executor and waits for it to terminate. */
    public void shutdownAndWait() {
        shutdownAndWait(Duration.ofSeconds(30), Duration.ofSeconds(10));
    }

    /**
     * Shuts down the delegate executor and waits for the given grace duration for it to terminate.
     * If this fails, tells the executor to {@link ExecutorService#shutdownNow()}), and waits for the die duration.
     */
    public void shutdownAndWait(Duration grace, Duration die) {
        executor.shutdown();
        try {
            executor.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            logger.log(Level.INFO, "Interrupted waiting for executor to complete", e);
        }
        if ( ! executor.isTerminated()) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(die.toMillis(), TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                logger.log(Level.WARNING, "Interrupted waiting for executor to die", e);
            }
            if ( ! executor.isTerminated())
                throw new RuntimeException("Failed to shut down executor");
        }
    }

}

