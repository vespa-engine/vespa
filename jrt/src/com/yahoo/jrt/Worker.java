// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


class Worker {

    private static final int WORK_LIMIT = 1024;

    private class Run implements Runnable {
        public void run() {
            try {
                Worker.this.run();
            } catch (Throwable problem) {
                parent.handleFailure(problem, Worker.this);
            }
        }
    }

    private static class CloseSocket implements Runnable {
        Connection connection;
        CloseSocket(Connection c) {
            connection = c;
        }
        public void run() {
            connection.closeSocket();
        }
    }

    private static class DoHandshakeWork implements Runnable {
        private final Connection connection;
        DoHandshakeWork(Connection c) {
            connection = c;
        }
        public void run() {
            connection.doHandshakeWork();
            connection.transportThread().handshakeWorkDone(connection);
        }
    }

    private static void preloadClassRequiredAtShutDown() {
        new CloseSocket(null);
    }
    private final Thread      thread;
    private final Transport   parent;
    private final ThreadQueue workQueue = new ThreadQueue();

    public Worker(Transport parent) {
        preloadClassRequiredAtShutDown();
        thread = new Thread(new Run(), parent.getName() + ".jrt-worker");
        this.parent = parent;
        thread.setDaemon(true);
        thread.start();
    }

    private void doLater(Runnable r) {
        if(!workQueue.enqueue(r, WORK_LIMIT)) {
            r.run();
        }
    }

    public void closeLater(Connection c) {
        doLater(new CloseSocket(c));
    }

    public void doHandshakeWork(Connection c) {
        doLater(new DoHandshakeWork(c));
    }

    private void run() {
        try {
            while (true) {
                ((Runnable) workQueue.dequeue()).run();
            }
        } catch (EndOfQueueException e) {}
    }

    public Worker shutdown() {
        workQueue.close();
        return this;
    }

    public void join() {
        while (true) {
            try {
                thread.join();
                return;
            } catch (InterruptedException e) {}
        }
    }
}
