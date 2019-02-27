// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


class Closer {

    private class Run implements Runnable {
        public void run() {
            try {
                Closer.this.run();
            } catch (Throwable problem) {
                parent.handleFailure(problem, Closer.this);
            }
        }
    }

    private Thread      thread = new Thread(new Run(), "<jrt-closer>");
    private Transport   parent;
    private ThreadQueue closeQueue = new ThreadQueue();

    public Closer(Transport parent) {
        this.parent = parent;
        thread.setDaemon(true);
        thread.start();
    }

    public void closeLater(Connection c) {
        if (!closeQueue.enqueue(c)) {
            c.closeSocket();
        }
    }

    private void run() {
        try {
            while (true) {
                ((Connection)closeQueue.dequeue()).closeSocket();
            }
        } catch (EndOfQueueException e) {}
    }

    public Closer shutdown() {
        closeQueue.close();
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
