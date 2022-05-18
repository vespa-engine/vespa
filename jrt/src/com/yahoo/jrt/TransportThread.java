// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A single reactor/scheduler thread inside a potentially
 * multi-threaded {@link Transport}.
 */
public class TransportThread {

    private static final int OPEN    = 1;
    private static final int CLOSING = 2;
    private static final int CLOSED  = 3;

    private class Run implements Runnable {
        public void run() {
            try {
                TransportThread.this.run();
            } catch (Throwable problem) {
                handleFailure(problem, TransportThread.this);
            }
        }
    }

    private class AddConnectionCmd implements Runnable {
        private final Connection conn;
        AddConnectionCmd(Connection conn) { this.conn = conn; }
        public void run() { handleAddConnection(conn); }
    }

    private class CloseConnectionCmd implements Runnable {
        private final Connection conn;
        CloseConnectionCmd(Connection conn) { this.conn = conn; }
        public void run() { handleCloseConnection(conn); }
    }

    private class EnableWriteCmd implements Runnable {
        private final Connection conn;
        EnableWriteCmd(Connection conn) { this.conn = conn; }
        public void run() { handleEnableWrite(conn); }
    }

    private class HandshakeWorkDoneCmd implements Runnable {
        private final Connection conn;
        HandshakeWorkDoneCmd(Connection conn) { this.conn = conn; }
        public void run() { handleHandshakeWorkDone(conn); }
    }

    private static class SyncCmd implements Runnable {
        boolean done = false;
        public synchronized void waitDone() {
            while (!done) {
                try { wait(); } catch (InterruptedException e) {}
            }
        }
        public synchronized void run() {
            done = true;
            notify();
        }
    }

    private static final Logger log = Logger.getLogger(TransportThread.class.getName());

    private final Transport parent;
    private final Thread    thread;
    private final Queue     queue;
    private final Queue     myQueue;
    private final Scheduler scheduler;
    private int             state;
    private final Selector  selector;

    private void handleAddConnection(Connection conn) {
        if (conn.isClosed()) {
            if (conn.hasSocket()) {
                parent.closeLater(conn);
            }
            return;
        }
        if (!conn.init(selector)) {
            handleCloseConnection(conn);
        }
    }

    private void handleCloseConnection(Connection conn) {
        if (conn.isClosed()) {
            return;
        }
        conn.fini();
        if (conn.hasSocket()) {
            parent.closeLater(conn);
        }
    }

    private void handleEnableWrite(Connection conn) {
        if (conn.isClosed()) {
            return;
        }
        conn.enableWrite();
    }

    private void handleHandshakeWorkDone(Connection conn) {
        if (conn.isClosed()) {
            return;
        }
        try {
            conn.handleHandshakeWorkDone();
        } catch (IOException e) {
            conn.setLostReason(e);
            handleCloseConnection(conn);
        }
    }

    private boolean postCommand(Runnable cmd) {
        int qlen;
        synchronized (this) {
            if (state == CLOSED) {
                return false;
            }
            queue.enqueue(cmd);
            qlen = queue.size();
        }
        if (qlen == parent.getEventsBeforeWakeup()) {
            selector.wakeup();
        }
        return true;
    }

    private void handleEvents() {
        synchronized (this) {
            queue.flush(myQueue);
        }
        while (!myQueue.isEmpty()) {
            ((Runnable)myQueue.dequeue()).run();
        }
    }

    private boolean handleIOEvents(Connection conn,
                                   SelectionKey key) {
        if (conn.isClosed()) {
            return true;
        }
        if (key.isReadable()) {
            try {
                conn.handleReadEvent();
            } catch (IOException e) {
                conn.setLostReason(e);
                return false;
            }
        }
        if (key.isWritable()) {
            try {
                conn.handleWriteEvent();
            } catch (IOException e) {
                conn.setLostReason(e);
                return false;
            }
        }
        return true;
    }

    TransportThread(Transport transport, int index) {
        parent    = transport;
        thread    = new Thread(new Run(), transport.getName() + ".jrt-transport." + index);
        queue     = new Queue();
        myQueue   = new Queue();
        scheduler = new Scheduler(System.currentTimeMillis());
        state     = OPEN;
        try {
            selector = Selector.open();
        } catch (Exception e) {
            throw new Error("Could not open transport selector", e);
        }
        thread.setDaemon(true);
        thread.start();
    }

    public Transport transport() {
        return parent;
    }

    /**
     * Proxy method used to dispatch fatal errors to the enclosing
     * Transport.
     *
     * @param problem the throwable causing the failure
     * @param context the object owning the crashing thread
     */
    void handleFailure(Throwable problem, Object context) {
        parent.handleFailure(problem, context);
    }

    /**
     * Adds a connection to the set of connections handled by this
     * TransportThread. Invoked by the {@link Connector} class.
     *
     * @param conn the connection to add
     */
    void addConnection(Connection conn) {
        if (!postCommand(new AddConnectionCmd(conn))) {
            perform(new CloseConnectionCmd(conn));
        }
    }

    /**
     * Requests an asynchronous close of a connection.
     *
     * @param conn the connection to close
     */
    void closeConnection(Connection conn) {
        postCommand(new CloseConnectionCmd(conn));
    }

    /**
     * Requests an asynchronous enabling of write events for a
     * connection.
     *
     * @param conn the connection to enable write events for
     */
    void enableWrite(Connection conn) {
        if (Thread.currentThread() == thread) {
            handleEnableWrite(conn);
        } else {
            postCommand(new EnableWriteCmd(conn));
        }
    }

    void handshakeWorkDone(Connection conn) {
        postCommand(new HandshakeWorkDoneCmd(conn));
    }

    /**
     * Creates a {@link Task} that can be scheduled for execution in
     * the transport thread.
     *
     * @return the newly created Task
     * @param cmd what to run when the task is executed
     */
    public Task createTask(Runnable cmd) {
        return new Task(scheduler, cmd);
    }

    /**
     * Performs the given command in such a way that it does not run
     * concurrently with the transport thread or other commands
     * performed by invoking this method. This method will continue to
     * work even after the transport thread has been shut down.
     *
     * @param cmd the command to perform
     */
    public void perform(Runnable cmd) {
        if (Thread.currentThread() == thread) {
            cmd.run();
            return;
        }
        if (!postCommand(cmd)) {
            join();
            synchronized (thread) {
                cmd.run();
            }
        }
    }

    /**
     * Wakes up this transport thread explicitly.
     */
    public void wakeup() {
        selector.wakeup();
    }

    /**
     * Wakes up this transport thread explicitly, but only if the
     * calling thread is not the transport thread itself.
     */
    public void wakeup_if_not_self() {
        if (Thread.currentThread() != thread) {
            wakeup();
        }
    }

    /**
     * Synchronizes with the transport thread. This method will block
     * until all commands issued before this method was invoked has
     * completed. If the transport thread has been shut down (or is in
     * the progress of being shut down) this method will instead wait
     * for the transport thread to complete, since no more commands
     * will be performed, and waiting would be forever. Invoking this
     * method from the transport thread is not a good idea.
     *
     * @return this object, to enable chaining
     */
    public TransportThread sync() {
        SyncCmd cmd = new SyncCmd();
        if (postCommand(cmd)) {
            cmd.waitDone();
        } else {
            join();
        }
        return this;
    }

    private void run() {
        while (state == OPEN) {

            // perform I/O selection
            try {
                selector.select(100);
            } catch (IOException e) {
                log.log(Level.WARNING, "error during select", e);
            }

            // handle internal events
            handleEvents();

            // handle I/O events
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                Connection conn = (Connection) key.attachment();
                keys.remove();
                if (!handleIOEvents(conn, key)) {
                    handleCloseConnection(conn);
                }
            }

            // check scheduled tasks
            scheduler.checkTasks(System.currentTimeMillis());
        }
        synchronized (this) {
            state = CLOSED;
        }
        handleEvents();
        Iterator<SelectionKey> keys = selector.keys().iterator();
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            Connection conn = (Connection) key.attachment();
            handleCloseConnection(conn);
        }
        try { selector.close(); } catch (Exception e) {}
        parent.notifyDone(this);
    }

    private synchronized void handleShutdown() {
        if (state == OPEN) {
            state = CLOSING;
        }
    }

    TransportThread shutdown() {
        postCommand(this::handleShutdown);
        return this;
    }

    void join() {
        while (true) {
            try {
                thread.join();
                return;
            } catch (InterruptedException e) {}
        }
    }

}
