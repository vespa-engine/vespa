// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.log.LogLevel;

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
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
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
    public void addRecurrentTask(final Task task) {
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
        enqueue(new MessageTask(msg, handler));
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
        enqueue(new ReplyTask(reply, handler));
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
        enqueue(Terminate.INSTANCE);
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
        while (true) {
            Task task = null;
            synchronized (this) {
                if (queue.isEmpty()) {
                    try {
                        wait(100);
                    } catch (final InterruptedException e) {
                        continue;
                    }
                }
                if (queue.size() > 0) {
                    task = queue.poll();
                }
            }
            if (task == Terminate.INSTANCE) {
                break;
            }
            if (task != null) {
                try {
                    task.run();
                } catch (final Exception e) {
                    log.log(LogLevel.ERROR, "An exception was thrown while running " + task.getClass().getName(), e);
                }
                try {
                    task.destroy();
                } catch (final Exception e) {
                    log.warning("An exception was thrown while destroying " + task.getClass().getName() + ": " +
                                e.toString());
                    log.warning("Someone, somewhere might have to wait indefinetly for something.");
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
        public void run();

        /**
         * <p>This method is called for all tasks, even if {@link #run()} was
         * never called.</p>
         */
        public void destroy();
    }

    private static class MessageTask implements Task {

        final MessageHandler handler;
        Message msg;

        MessageTask(final Message msg, final MessageHandler handler) {
            this.msg = msg;
            this.handler = handler;
        }

        @Override
        public void run() {
            final Message msg = this.msg;
            this.msg = null;
            handler.handleMessage(msg);
        }

        @Override
        public void destroy() {
            if (msg != null) {
                msg.discard();
            }
        }
    }

    private static class ReplyTask implements Task {

        final ReplyHandler handler;
        Reply reply;

        ReplyTask(final Reply reply, final ReplyHandler handler) {
            this.reply = reply;
            this.handler = handler;
        }

        @Override
        public void run() {
            final Reply reply = this.reply;
            this.reply = null;
            handler.handleReply(reply);
        }

        @Override
        public void destroy() {
            if (reply != null) {
                reply.discard();
            }
        }
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

    private static class Terminate implements Task {

        static final Terminate INSTANCE = new Terminate();

        @Override
        public void run() {
            // empty
        }

        @Override
        public void destroy() {
            // empty
        }
    }
}
