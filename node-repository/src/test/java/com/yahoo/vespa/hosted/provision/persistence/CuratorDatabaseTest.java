// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorOperation;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.curator.transaction.TransactionChanges;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Tests the curator db directly.
 * This verifies the details of the current implementation of the database, not just its API;
 * breaking this does not necessarily mean that a change is wrong.
 *
 * @author bratseth
 */
public class CuratorDatabaseTest {

    @Test
    public void testTransactionsIncreaseCounter() throws Exception {
        MockCurator curator = new MockCurator();
        CuratorDatabase database = new CuratorDatabase(curator, Path.fromString("/"), true);

        assertEquals(0L, (long)curator.counter("/changeCounter").get().get().postValue());

        commitCreate("/1", database);
        commitCreate("/2", database);
        commitCreate("/1/1", database);
        commitCreate("/2/1", database);
        assertEquals(8L, (long)curator.counter("/changeCounter").get().get().postValue());

        List<String> children1Call1 = database.getChildren(Path.fromString("/1"));
        List<String> children1Call2 = database.getChildren(Path.fromString("/1"));
        assertTrue("We reuse cached data when there are no commits", children1Call1 == children1Call2);

        assertEquals(1, database.getChildren(Path.fromString("/2")).size());
        commitCreate("/2/2", database);
        List<String> children1Call3 = database.getChildren(Path.fromString("/1"));
        assertEquals(2, database.getChildren(Path.fromString("/2")).size());
        assertFalse("We do not reuse cached data in different parts of the tree when there are commits",
                    children1Call3 == children1Call2);
    }

    @Test
    public void testCacheInvalidation() throws Exception {
        MockCurator curator = new MockCurator();
        CuratorDatabase database = new CuratorDatabase(curator, Path.fromString("/"), true);

        assertEquals(0L, (long)curator.counter("/changeCounter").get().get().postValue());
        commitCreate("/1", database);
        assertArrayEquals(new byte[0], database.getData(Path.fromString("/1")).get());
        commitReadingWrite("/1", "hello".getBytes(), database);
        // Data cached during commit of write transaction. Should be invalid now, and re-read.
        assertEquals(4L, (long)curator.counter("/changeCounter").get().get().postValue());
        assertArrayEquals("hello".getBytes(), database.getData(Path.fromString("/1")).get());

        assertEquals(0, database.getChildren(Path.fromString("/1")).size());
        commitCreate("/1/1", database);
        assertEquals(1, database.getChildren(Path.fromString("/1")).size());
    }

    @Test
    public void testTransactionsWithDeactivatedCache() throws Exception {
        MockCurator curator = new MockCurator();
        CuratorDatabase database = new CuratorDatabase(curator, Path.fromString("/"), false);

        assertEquals(0L, (long)curator.counter("/changeCounter").get().get().postValue());

        commitCreate("/1", database);
        commitCreate("/2", database);
        commitCreate("/1/1", database);
        commitCreate("/2/1", database);
        assertEquals(8L, (long)curator.counter("/changeCounter").get().get().postValue());

        List<String> children1Call0 = database.getChildren(Path.fromString("/1")); // prime the db; this call returns a different instance
        List<String> children1Call1 = database.getChildren(Path.fromString("/1"));
        List<String> children1Call2 = database.getChildren(Path.fromString("/1"));
        assertTrue("No cache, no reused data", children1Call1 != children1Call2);
    }

    @Test
    public void testThatCounterIncreasesExactlyOnCommitFailure() throws Exception {
        MockCurator curator = new MockCurator();
        CuratorDatabase database = new CuratorDatabase(curator, Path.fromString("/"), true);

        assertEquals(0L, (long)curator.counter("/changeCounter").get().get().postValue());

        try {
            commitCreate("/1/2", database); // fail as parent does not exist
            fail("Expected exception");
        }
        catch (Exception expected) {
            // expected because the parent does not exist
        }
        // Counter increased once, since prepare failed.
        assertEquals(1L, (long)curator.counter("/changeCounter").get().get().postValue());

        try {
            commitFailing(database); // fail during commit
            fail("Expected exception");
        }
        catch (Exception expected) { }
        // Counter increased, even though commit failed.
        assertEquals(3L, (long)curator.counter("/changeCounter").get().get().postValue());
    }

    private void commitCreate(String path, CuratorDatabase database) {
        NestedTransaction t = new NestedTransaction();
        CuratorTransaction c = database.newCuratorTransactionIn(t);
        c.add(CuratorOperations.create(path));
        t.commit();
    }

    private void commitReadingWrite(String path, byte[] data, CuratorDatabase database) {
        NestedTransaction transaction = new NestedTransaction();
        byte[] oldData = database.getData(Path.fromString(path)).get();
        CuratorTransaction curatorTransaction = database.newCuratorTransactionIn(transaction);
        // Add a dummy operation which reads the data and populates the cache during commit of the write.
        curatorTransaction.add(new DummyOperation(() -> assertArrayEquals(oldData, database.getData(Path.fromString(path)).get())));
        curatorTransaction.add(CuratorOperations.setData(path, data));
        transaction.commit();
    }

    /** Commit an operation which fails during commit. */
    private void commitFailing(CuratorDatabase database) {
        NestedTransaction t = new NestedTransaction();
        CuratorTransaction c = database.newCuratorTransactionIn(t);
        c.add(new DummyOperation(() -> { throw new RuntimeException(); }));
        t.commit();
    }

    static class DummyOperation implements CuratorOperation {

        private final Runnable task;

        public DummyOperation(Runnable task) {
            this.task = task;
        }

        @SuppressWarnings("deprecation")
        @Override
        public org.apache.curator.framework.api.transaction.CuratorTransaction and(org.apache.curator.framework.api.transaction.CuratorTransaction transaction) {
            task.run();
            return transaction;
        }

        @Override
        public void check(Curator curator, TransactionChanges changes) { }

    }

}
