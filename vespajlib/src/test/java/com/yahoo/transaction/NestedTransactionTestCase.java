// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.transaction;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class NestedTransactionTestCase {

    @Test
    public void testNestedTransaction() {
        NestedTransaction t = new NestedTransaction();
        t.add(new TransactionTypeB("B1"), TransactionTypeC.class);
        t.add(new TransactionTypeC("C1"));
        t.add(new TransactionTypeA("A1"), TransactionTypeB.class);
        t.add(new TransactionTypeA("A2"));
        t.add(new TransactionTypeC("C2"));

        // Add two tasks to run after commit
        MutableInteger tasksRun = new MutableInteger();
        t.onCommitted(() -> { tasksRun.value++; });
        t.onCommitted(() -> { tasksRun.value++; });

        assertEquals(3, t.transactions().size());
        assertEquals(TransactionTypeA.class, t.transactions().get(0).getClass());
        assertEquals(TransactionTypeB.class, t.transactions().get(1).getClass());
        assertEquals(TransactionTypeC.class, t.transactions().get(2).getClass());

        assertEquals("A1", ((MockOperation)t.transactions().get(0).operations().get(0)).name);
        assertEquals("A2", ((MockOperation)t.transactions().get(0).operations().get(1)).name);
        assertEquals("B1", ((MockOperation)t.transactions().get(1).operations().get(0)).name);
        assertEquals("C1", ((MockOperation)t.transactions().get(2).operations().get(0)).name);
        assertEquals("C2", ((MockOperation)t.transactions().get(2).operations().get(1)).name);

        t.commit();
        assertTrue(((MockTransaction)t.transactions().get(0)).committed);
        assertTrue(((MockTransaction)t.transactions().get(1)).committed);
        assertTrue(((MockTransaction)t.transactions().get(2)).committed);
        assertEquals("After commit tasks are run", 2, tasksRun.value);
    }

    @Test
    public void testNestedTransactionFailingOnCommit() {
        NestedTransaction t = new NestedTransaction();
        t.add(new TransactionTypeC("C1"));
        t.add(new TransactionTypeA("A1"), TransactionTypeB.class, FailAtCommitTransactionType.class);
        t.add(new TransactionTypeA("A2"));
        t.add(new FailAtCommitTransactionType("Fail"), TransactionTypeC.class);
        t.add(new TransactionTypeC("C2"));
        t.add(new TransactionTypeB("B1"), TransactionTypeC.class, FailAtCommitTransactionType.class);

        // Add task to run after commit
        MutableInteger tasksRun = new MutableInteger();
        t.onCommitted(() -> {
            tasksRun.value++;
        });

        assertEquals(4, t.transactions().size());
        assertEquals(TransactionTypeA.class, t.transactions().get(0).getClass());
        assertEquals(TransactionTypeB.class, t.transactions().get(1).getClass());
        assertEquals(FailAtCommitTransactionType.class, t.transactions().get(2).getClass());
        assertEquals(TransactionTypeC.class, t.transactions().get(3).getClass());

        try { t.commit(); } catch (IllegalStateException expected) { }
        assertTrue(((MockTransaction)t.transactions().get(0)).rolledback);
        assertTrue(((MockTransaction)t.transactions().get(1)).rolledback);
        assertFalse(((MockTransaction) t.transactions().get(2)).committed);
        assertEquals("After commit tasks are not run", 0, tasksRun.value);
    }

    @Test
    public void testConflictingOrdering() {
        NestedTransaction t = new NestedTransaction();
        t.add(new TransactionTypeA("A1"), TransactionTypeB.class);
        t.add(new TransactionTypeB("B1"), TransactionTypeC.class);
        t.add(new TransactionTypeC("C1"), TransactionTypeA.class);
        try {
            t.commit();
            fail("Expected exception");
        }
        catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testMoreThanOneCommitThrows() {
        NestedTransaction t = new NestedTransaction();
        t.add(new TransactionTypeA("A1"), TransactionTypeB.class);
        t.commit();
        try {
            t.commit();
            fail("Expected exception");
        }
        catch (IllegalStateException expected) {
        }
    }

    private static class TransactionTypeA extends MockTransaction {
        public TransactionTypeA(String name) { super(name); }
    }

    private static class TransactionTypeB extends MockTransaction {
        public TransactionTypeB(String name) { super(name); }
    }

    private static class TransactionTypeC extends MockTransaction {
        public TransactionTypeC(String name) { super(name); }
    }

    private static class FailAtCommitTransactionType extends MockTransaction {
        public FailAtCommitTransactionType(String name) { super(name); }
        @Override
        public void commit() {
            throw new RuntimeException();
        }
    }

    private static class MockTransaction extends AbstractTransaction {

        public boolean prepared = false, committed = false, rolledback = false;

        public MockTransaction(String name) {
            add(new MockOperation(name));
        }

        @Override
        public void prepare() {
            prepared = true;
        }

        @Override
        public void commit() {
            if ( ! prepared)
                throw new IllegalStateException("Commit before prepare");
            committed = true;
        }

        @Override
        public void rollbackOrLog() {
            if ( ! committed)
                throw new IllegalStateException("Rollback before commit");
            rolledback = true;
        }

    }

    private static class MockOperation implements Transaction.Operation {

        public String name;

        public MockOperation(String name) {
            this.name = name;
        }

    }

    private static class MutableInteger {

        public int value = 0;

    }

}
