// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


class Connector {

    private class Run implements Runnable {
        public void run() {
            try {
                Connector.this.run();
            } catch (Throwable problem) {
                parent.handleFailure(problem, Connector.this);
            }
        }
    }

    private Thread      thread = new Thread(new Run(), "<jrt-connector>");
    private Transport   parent;
    private ThreadQueue connectQueue = new ThreadQueue();
    private boolean     done = false;
    private boolean     exit = false;

    public Connector(Transport parent) {
        this.parent = parent;
        thread.setDaemon(true);
        thread.start();
    }

    public void connectLater(Connection c) {
        if ( ! connectQueue.enqueue(c)) {
            parent.addConnection(c);
        }
    }

    private void run() {
        try {
            while (true) {
                Connection conn = (Connection) connectQueue.dequeue();
                parent.addConnection(conn.connect());
            }
        } catch (EndOfQueueException e) {}
        synchronized (this) {
            done = true;
            notifyAll();
            while (!exit) {
                try { wait(); } catch (InterruptedException x) {}
            }
        }
    }

    public Connector shutdown() {
        connectQueue.close();
        return this;
    }

    public synchronized void waitDone() {
        while (!done) {
            try { wait(); } catch (InterruptedException x) {}
        }
    }

    public synchronized Connector exit() {
        exit = true;
        notifyAll();
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
