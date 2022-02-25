// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.SystemTimer;

import java.time.Duration;
import java.util.logging.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * <p>This class implements a single thread that is able to process arbitrary
 * tasks. Tasks are enqueued using the synchronized {@link #enqueue(Task)}
 * method, and are run in the order they were enqueued.</p>
 *
 * @author Simon Thoresen Hult
 */
public class Messenger implements Runnable {

    private static final Logger log = Logger.getLogger(Messenger.class.getName());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final List<Task> children = new ArrayList<>();
    private final Queue<Task> queue = new ArrayDeque<>();

    private final Thread thread = new Thread(this, "Messenger");

    public Messenger() {
        thread.setDaemon(true);
    }

    /**
     * <p>Adds a recurrent task to this that is to be run for every iteration of
     * the main loop. This task must be very light-weight as to not block the
     * messenger. Note that this method is NOT thread-safe, so it should NOT be
     * used after calling {@link #start()}.</p>
     *
     * @param task The task to add.
     */
    void addRecurrentTask(final Task task) {
        children.add(task);
    }

    /**
     * <p>Starts the internal thread. This must be done AFTER all recurrent
     * tasks have been added.</p>
     *
     * @see #addRecurrentTask(Task)
     */
    public void start() {
        thread.start();
    }

    /**
     * <p>Convenience method to post a {@link Task} that delivers a {@link
     * Message} to a {@link MessageHandler} to the queue of tasks to be
     * executed.</p>
     *
     * @param msg     The message to send.
     * @param handler The handler to send to.
     */
    public void deliverMessage(final Message msg, final MessageHandler handler) {
        if (destroyed.get()) {
            msg.discard();
        } else {
            handler.handleMessage(msg);
        }
    }

    /**
     * <p>Convenience method to post a {@link Task} that delivers a {@link
     * Reply} to a {@link ReplyHandler} to the queue of tasks to be
     * executed.</p>
     *
     * @param reply   The reply to return.
     * @param handler The handler to return to.
     */
    public void deliverReply(final Reply reply, final ReplyHandler handler) {
        if (destroyed.get()) {
            reply.discard();
        } else {
            handler.handleReply(reply);
        }
    }

    /**
     * <p>Enqueues the given task in the list of tasks that this worker is to
     * process. If this thread has been destroyed previously, this method
     * invokes {@link Messenger.Task#destroy()}.</p>
     *
     * @param task The task to enqueue.
     */
    public void enqueue(final Task task) {
        if (destroyed.get()) {
            task.destroy();
            return;
        }
        synchronized (this) {
            queue.offer(task);
            if (queue.size() == 1) {
                notify();
            }
        }
    }

    /**
     * <p>Handshakes with the internal thread. If this method is called using
     * the messenger thread, this will deadlock.</p>
     */
    public void sync() {
        if (Thread.currentThread() == thread) {
            return; // no need to wait for self
        }
        final SyncTask task = new SyncTask();
        enqueue(task);
        task.await();
    }

    /**
     * <p>Sets the destroyed flag to true. The very first time this method is
     * called, it cleans up all its dependencies.  Even if you retain a
     * reference to this object, all of its content is allowed to be garbage
     * collected.</p>
     *
     * @return True if content existed and was destroyed.
     */
    public boolean destroy() {
        boolean done = false;
        enqueue(TERMINATE);
        if (!destroyed.getAndSet(true)) {
            try {
                synchronized (this) {
                    while (!queue.isEmpty()) {
                        wait();
                    }
                }
                thread.join();
            } catch (final InterruptedException e) {
                // ignore
            }
            done = true;
        }
        return done;
    }

    @Override
    public void run() {
        long timeoutMS = SystemTimer.adjustTimeoutByDetectedHz(Duration.ofMillis(100)).toMillis();
        while (true) {
            Task task = null;
            synchronized (this) {
                if (queue.isEmpty()) {
                    try {
                        if (children.isEmpty()) {
                            wait();
                        } else {
                            wait(timeoutMS);
                        }
                    } catch (final InterruptedException e) {
                        continue;
                    }
                }
                if (queue.size() > 0) {
                    task = queue.poll();
                }
            }
            if (task == TERMINATE) {
                break;
            }
            if (task != null) {
                try {
                    task.run();
                } catch (final Exception e) {
                    log.log(Level.SEVERE, "An exception was thrown while running " + task.getClass().getName(), e);
                }
                try {
                    task.destroy();
                } catch (final Exception e) {
                    log.warning("An exception was thrown while destroying " + task.getClass().getName() + ": " + e);
                    log.warning("Someone, somewhere might have to wait indefinitely for something.");
                }
            }
            for (final Task child : children) {
                child.run();
            }
        }
        for (final Task child : children) {
            child.destroy();
        }
        synchronized (this) {
            while (!queue.isEmpty()) {
                final Task task = queue.poll();
                task.destroy();
            }
            notify();
        }
    }

    /**
     * <p>Defines the required interface for tasks to be posted to this
     * worker.</p>
     */
    public interface Task {

        /**
         * <p>This method is called when being executed.</p>
         */
        void run();

        /**
         * <p>This method is called for all tasks, even if {@link #run()} was
         * never called.</p>
         */
        void destroy();
    }

    private static class SyncTask implements Task {

        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void run() {
            // empty
        }

        @Override
        public void destroy() {
            latch.countDown();
        }

        public void await() {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                // ignore
            }
        }
    }

    private static final Task TERMINATE = new Task() {
        @Override public void run() { }
        @Override public void destroy() { }
    };

}
