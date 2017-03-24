// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

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
    public void testTransactionsIncreaseTimer() throws Exception {
        MockCurator curator = new MockCurator();
        CuratorDatabase database = new CuratorDatabase(curator, Path.fromString("/"), true);

        assertEquals(0L, (long)curator.counter("/changeCounter").get().get().postValue());

        commitCreate("/1", database);
        commitCreate("/2", database);
        commitCreate("/1/1", database);
        commitCreate("/2/1", database);
        assertEquals(4L, (long)curator.counter("/changeCounter").get().get().postValue());

        List<String> children1Call0 = database.getChildren(Path.fromString("/1")); // prime the db; this call returns a different instance
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
    public void testTransactionsWithDeactivatedCache() throws Exception {
        MockCurator curator = new MockCurator();
        CuratorDatabase database = new CuratorDatabase(curator, Path.fromString("/"), false);

        assertEquals(0L, (long)curator.counter("/changeCounter").get().get().postValue());

        commitCreate("/1", database);
        commitCreate("/2", database);
        commitCreate("/1/1", database);
        commitCreate("/2/1", database);
        assertEquals(4L, (long)curator.counter("/changeCounter").get().get().postValue());

        List<String> children1Call0 = database.getChildren(Path.fromString("/1")); // prime the db; this call returns a different instance
        List<String> children1Call1 = database.getChildren(Path.fromString("/1"));
        List<String> children1Call2 = database.getChildren(Path.fromString("/1"));
        assertTrue("No cache, no reused data", children1Call1 != children1Call2);
    }

    @Test
    public void testThatCounterIncreasesAlsoOnCommitFailure() throws Exception {
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
        assertEquals(1L, (long)curator.counter("/changeCounter").get().get().postValue());
    }

    @Test
    public void testThatCounterIncreasesAlsoOnCommitFailureFromExistingTransaction() throws Exception {
        MockCurator curator = new MockCurator();
        CuratorDatabase database = new CuratorDatabase(curator, Path.fromString("/"), true);

        assertEquals(0L, (long)curator.counter("/changeCounter").get().get().postValue());

        try {
            NestedTransaction t = new NestedTransaction();
            CuratorTransaction separateC = new CuratorTransaction(curator);
            separateC.add(CuratorOperations.create("/1/2")); // fail as parent does not exist
            t.add(separateC);

            CuratorTransaction c = database.newCuratorTransactionIn(t);
            c.add(CuratorOperations.create("/1")); // does not fail

            t.commit();
            fail("Expected exception");
        }
        catch (Exception expected) {
            // expected because the parent does not exist
        }
        assertEquals(1L, (long)curator.counter("/changeCounter").get().get().postValue());
    }

    private void commitCreate(String path, CuratorDatabase database) {
        NestedTransaction t = new NestedTransaction();
        CuratorTransaction c = database.newCuratorTransactionIn(t);
        c.add(CuratorOperations.create(path));
        t.commit();
    }

}
