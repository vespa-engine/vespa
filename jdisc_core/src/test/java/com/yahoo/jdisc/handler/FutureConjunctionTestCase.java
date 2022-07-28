// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class FutureConjunctionTestCase {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Test
    void requireThatAllFuturesAreWaitedFor() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        FutureConjunction future = new FutureConjunction();
        CompletableFuture<Boolean> cf = new CompletableFuture<>();
        cf.completeAsync(() -> {
            try {
                return latch.await(600, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                return false;
            }
        }, executor);
        future.addOperand(cf);
        try {
            future.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        latch.countDown();
        assertTrue(future.get(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatGetReturnValueIsAConjunction() throws Exception {
        assertTrue(tryGet(true));
        assertTrue(tryGet(true, true));
        assertTrue(tryGet(true, true, true));

        assertFalse(tryGet(false));
        assertFalse(tryGet(false, true));
        assertFalse(tryGet(true, false));
        assertFalse(tryGet(false, true, true));
        assertFalse(tryGet(true, false, true));
        assertFalse(tryGet(true, true, false));
        assertFalse(tryGet(false, false, true));
        assertFalse(tryGet(false, true, false));
        assertFalse(tryGet(true, false, false));
    }

    @Test
    void requireThatIsDoneReturnValueIsAConjunction() {
        assertTrue(tryIsDone(true));
        assertTrue(tryIsDone(true, true));
        assertTrue(tryIsDone(true, true, true));

        assertFalse(tryIsDone(false));
        assertFalse(tryIsDone(false, true));
        assertFalse(tryIsDone(true, false));
        assertFalse(tryIsDone(false, true, true));
        assertFalse(tryIsDone(true, false, true));
        assertFalse(tryIsDone(true, true, false));
        assertFalse(tryIsDone(false, false, true));
        assertFalse(tryIsDone(false, true, false));
        assertFalse(tryIsDone(true, false, false));
    }

    @Test
    void requireThatCancelReturnValueIsAConjuction() {
        assertTrue(tryCancel(true));
        assertTrue(tryCancel(true, true));
        assertTrue(tryCancel(true, true, true));

        assertFalse(tryCancel(false));
        assertFalse(tryCancel(false, true));
        assertFalse(tryCancel(true, false));
        assertFalse(tryCancel(false, true, true));
        assertFalse(tryCancel(true, false, true));
        assertFalse(tryCancel(true, true, false));
        assertFalse(tryCancel(false, false, true));
        assertFalse(tryCancel(false, true, false));
        assertFalse(tryCancel(true, false, false));
    }

    @Test
    void requireThatIsCancelledReturnValueIsAConjuction() {
        assertTrue(tryIsCancelled(true));
        assertTrue(tryIsCancelled(true, true));
        assertTrue(tryIsCancelled(true, true, true));

        assertFalse(tryIsCancelled(false));
        assertFalse(tryIsCancelled(false, true));
        assertFalse(tryIsCancelled(true, false));
        assertFalse(tryIsCancelled(false, true, true));
        assertFalse(tryIsCancelled(true, false, true));
        assertFalse(tryIsCancelled(true, true, false));
        assertFalse(tryIsCancelled(false, false, true));
        assertFalse(tryIsCancelled(false, true, false));
        assertFalse(tryIsCancelled(true, false, false));
    }

    @Test
    void requireThatConjunctionCanBeListenedTo() throws InterruptedException {
        FutureConjunction conjunction = new FutureConjunction();
        RunnableLatch listener = new RunnableLatch();
        conjunction.addListener(listener, Runnable::run);
        assertTrue(listener.await(600, TimeUnit.SECONDS));

        conjunction = new FutureConjunction();
        FutureBoolean foo = new FutureBoolean();
        conjunction.addOperand(foo);
        FutureBoolean bar = new FutureBoolean();
        conjunction.addOperand(bar);
        listener = new RunnableLatch();
        conjunction.addListener(listener, Runnable::run);
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        foo.set(true);
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        bar.set(true);
        assertTrue(listener.await(600, TimeUnit.SECONDS));

        conjunction = new FutureConjunction();
        foo = new FutureBoolean();
        conjunction.addOperand(foo);
        bar = new FutureBoolean();
        conjunction.addOperand(bar);
        listener = new RunnableLatch();
        conjunction.addListener(listener, Runnable::run);
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        bar.set(true);
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        foo.set(true);
        assertTrue(listener.await(600, TimeUnit.SECONDS));
    }

    private static boolean tryGet(boolean... operands) throws Exception {
        FutureConjunction foo = new FutureConjunction();
        FutureConjunction bar = new FutureConjunction();
        for (boolean op : operands) {
            foo.addOperand(MyFuture.newInstance(op));
            bar.addOperand(MyFuture.newInstance(op));
        }
        boolean fooResult = foo.get();
        boolean barResult = foo.get(0, TimeUnit.SECONDS);
        assertEquals(fooResult, barResult);
        return fooResult;
    }

    private static boolean tryIsDone(boolean... operands) {
        FutureConjunction foo = new FutureConjunction();
        for (boolean op : operands) {
            foo.addOperand(MyFuture.newIsDone(op));
        }
        return foo.isDone();
    }

    private static boolean tryCancel(boolean... operands) {
        FutureConjunction foo = new FutureConjunction();
        FutureConjunction bar = new FutureConjunction();
        for (boolean op : operands) {
            foo.addOperand(MyFuture.newCanCancel(op));
            bar.addOperand(MyFuture.newCanCancel(op));
        }
        boolean fooResult = foo.cancel(true);
        boolean barResult = foo.cancel(false);
        assertEquals(fooResult, barResult);
        return fooResult;
    }

    private static boolean tryIsCancelled(boolean... operands) {
        FutureConjunction foo = new FutureConjunction();
        for (boolean op : operands) {
            foo.addOperand(MyFuture.newIsCancelled(op));
        }
        return foo.isCancelled();
    }

    private static class FutureBoolean extends CompletableFuture<Boolean> {

        public boolean set(Boolean val) {
            return super.complete(val);
        }
    }

    private static class MyFuture extends CompletableFuture<Boolean> {

        final boolean value;
        final boolean isDone;
        final boolean canCancel;
        final boolean isCancelled;

        MyFuture(boolean value, boolean isDone, boolean canCancel, boolean isCancelled) {
            this.value = value;
            this.isDone = isDone;
            this.canCancel = canCancel;
            this.isCancelled = isCancelled;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return canCancel;
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        @Override
        public boolean isDone() {
            return isDone;
        }

        @Override
        public Boolean get() {
            return value;
        }

        @Override
        public Boolean get(long timeout, TimeUnit unit) {
            return value;
        }

        static CompletableFuture<Boolean> newInstance(boolean value) {
            return new MyFuture(value, false, false, false);
        }

        static CompletableFuture<Boolean> newIsDone(boolean isDone) {
            return new MyFuture(false, isDone, false, false);
        }

        static CompletableFuture<Boolean> newCanCancel(boolean canCancel) {
            return new MyFuture(false, false, canCancel, false);
        }

        static CompletableFuture<Boolean> newIsCancelled(boolean isCancelled) {
            return new MyFuture(false, false, false, isCancelled);
        }
    }
}
