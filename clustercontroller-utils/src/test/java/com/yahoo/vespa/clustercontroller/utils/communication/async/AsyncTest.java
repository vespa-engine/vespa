// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

import org.junit.Test;

import java.util.LinkedList;

import static org.junit.Assert.*;

public class AsyncTest {

    @Test
    public void testListeners() {
        AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test");
        class Listener implements AsyncCallback<String> {
            boolean called = false;
            @Override
            public void done(AsyncOperation<String> op) {
                called = true;
            }
        }
        Listener l1 = new Listener();
        Listener l2 = new Listener();
        Listener l3 = new Listener();
        Listener l4 = new Listener();
        op.register(l1);
        op.register(l2);
        op.register(l3);
        op.unregister(l1);
        op.setResult("foo");
        op.register(l4);
        // Listener that is unregistered is not called
        assertEquals(false, l1.called);
        // Listener that is registered is called
        assertEquals(true, l2.called);
        // Multiple listeners supported
        assertEquals(true, l3.called);
        // Listener called directly when registered after result is set
        assertEquals(true, l4.called);
    }

    @Test
    public void testMultipleResultSetters() {
        {
            AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test");
            op.setResult("foo");
            op.setResult("bar");
            assertEquals("foo", op.getResult());
        }
        {
            AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test");
            op.setResult("foo");
            op.setFailure(new Exception("bar"));
            assertEquals("foo", op.getResult());
            assertEquals(true, op.isSuccess());
        }
        {
            AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test");
            op.setFailure(new Exception("bar"));
            op.setResult("foo");
            assertNull(op.getResult());
            assertEquals(false, op.isSuccess());
            assertEquals("bar", op.getCause().getMessage());
        }
    }

    @Test
    public void testPartialResultOnFailure() {
        AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test");
        op.setFailure(new Exception("bar"), "foo");
        assertEquals("foo", op.getResult());
        assertEquals(false, op.isSuccess());
        assertEquals("bar", op.getCause().getMessage());
    }

    @Test
    public void testListenImpl() {
        class ListenImpl extends AsyncOperationListenImpl<String> {
            public ListenImpl(AsyncOperation<String> op) {
                super(op);
            }
        };
        class Listener implements AsyncCallback<String> {
            int calls = 0;
            @Override
            public void done(AsyncOperation<String> op) {
                ++calls;
            }
        }
        AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test");
        ListenImpl impl = new ListenImpl(op);
        Listener l1 = new Listener();
        impl.register(l1);
        impl.notifyListeners();
        impl.notifyListeners();
        impl.notifyListeners();
        assertEquals(1, l1.calls);
    }

    @Test
    public void testRedirectedOperation() {
        {
            final AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test", "desc");
            AsyncOperation<Integer> deleteRequest = new RedirectedAsyncOperation<String, Integer>(op) {
                @Override
                public Integer getResult() {
                    return Integer.valueOf(op.getResult());
                }
            };
            final LinkedList<Integer> result = new LinkedList<>();
            deleteRequest.register(new AsyncCallback<Integer>() {
                @Override
                public void done(AsyncOperation<Integer> op) {
                    result.add(op.getResult());
                }
            });
            assertNull(deleteRequest.getProgress());
            op.setResult("123");
            assertEquals(true, deleteRequest.isDone());
            assertEquals(true, deleteRequest.isSuccess());
            assertEquals(new Integer(123), deleteRequest.getResult());
            assertEquals("desc", deleteRequest.getDescription());
            assertEquals("test", deleteRequest.getName());
            assertEquals(1, result.size());
            assertEquals(Integer.valueOf(123), result.getFirst());
            assertEquals(Double.valueOf(1.0), deleteRequest.getProgress());

            // Get some extra coverage
            deleteRequest.cancel();
            deleteRequest.isCanceled();
            deleteRequest.unregister(null);
        }
        {
            final AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test", "desc");
            AsyncOperation<Integer> deleteRequest = new RedirectedAsyncOperation<String, Integer>(op) {
                @Override
                public Integer getResult() {
                    return Integer.valueOf(op.getResult());
                }
            };
            op.setFailure(new Exception("foo"));
            assertEquals(true, deleteRequest.isDone());
            assertEquals("foo", deleteRequest.getCause().getMessage());
            assertEquals(false, deleteRequest.isSuccess());
            deleteRequest.getProgress();
        }
    }

    @Test
    public void testRedirectOnSuccessOperation() {
        {
            final AsyncOperationImpl<Integer> target = new AsyncOperationImpl<>("foo");
            SuccessfulAsyncCallback<String, Integer> callback = new SuccessfulAsyncCallback<String, Integer>(target) {
                @Override
                public void successfullyDone(AsyncOperation<String> source) {
                    target.setResult(Integer.valueOf(source.getResult()));
                }
            };
            AsyncOperationImpl<String> source = new AsyncOperationImpl<>("source");
            source.register(callback);
            source.setResult("5");
            assertTrue(target.isDone());
            assertTrue(target.isSuccess());
            assertEquals(new Integer(5), target.getResult());
        }
        {
            final AsyncOperationImpl<Integer> target = new AsyncOperationImpl<>("foo");
            SuccessfulAsyncCallback<String, Integer> callback = new SuccessfulAsyncCallback<String, Integer>(target) {
                @Override
                public void successfullyDone(AsyncOperation<String> source) {
                    target.setResult(Integer.valueOf(source.getResult()));
                }
            };
            AsyncOperationImpl<String> source = new AsyncOperationImpl<>("source");
            source.register(callback);
            source.setFailure(new RuntimeException("foo"));
            assertTrue(target.isDone());
            assertFalse(target.isSuccess());
            assertEquals("foo", target.getCause().getMessage());
        }
    }

    private abstract class StressThread implements Runnable {
        private final Object monitor;
        private boolean running = true;

        public StressThread(Object monitor) { this.monitor = monitor; }

        public void stop() {
            synchronized (monitor) {
                running = false;
                monitor.notifyAll();
            }
        }

        @Override
        public void run() {
            try{ synchronized (monitor) { while (running) {
                if (hasTask()) {
                    doTask();
                } else {
                    monitor.wait(1000);
                }
            } } } catch (Exception e) {}
        }

        public abstract boolean hasTask();
        public abstract void doTask();
    }

    private abstract class AsyncOpStressThread extends StressThread {
        public AsyncOperationImpl<String> op;
        public AsyncOpStressThread(Object monitor) { super(monitor); }
        @Override
        public boolean hasTask() { return op != null; }
    }

    private class Completer extends AsyncOpStressThread {
        public Completer(Object monitor) { super(monitor); }
        @Override
        public void doTask() { op.setResult("foo"); op = null; }
    }

    private class Listener extends AsyncOpStressThread implements AsyncCallback<String> {
        int counter = 0;
        int unset = 0;
        int priorReg = 0;
        public Listener(Object monitor) { super(monitor); }
        @Override
        public void done(AsyncOperation<String> op) {
            synchronized (this) {
                if (op.getResult() == null) ++unset;
                ++counter;
            }
        }

        @Override
        public void doTask() {
            op.register(this);
            if (!op.isDone()) ++priorReg;
            op = null;
        }
    }

    @Test
    public void testStressCompletionAndRegisterToDetectRace() throws Exception {
        int iterations = 1000;
        Object monitor = new Object();
        Completer completer = new Completer(monitor);
        Listener listener = new Listener(monitor);
        Thread t1 = new Thread(completer);
        Thread t2 = new Thread(listener);
        try{
            t1.start();
            t2.start();
            for (int i=0; i<iterations; ++i) {
                AsyncOperationImpl<String> op = new AsyncOperationImpl<>("test");
                synchronized (monitor) {
                    completer.op = op;
                    listener.op = op;
                    monitor.notifyAll();
                }
                while (completer.op != null || listener.op != null) {
                    try{ Thread.sleep(0); } catch (InterruptedException e) {}
                }
            }
        } finally {
            completer.stop();
            listener.stop();
            t1.join();
            t2.join();
        }
        assertEquals(0, listener.unset);
        assertEquals(iterations, listener.counter);
    }

}
