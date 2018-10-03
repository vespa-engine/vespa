// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.recipes.CuratorLock;
import com.yahoo.vespa.curator.recipes.CuratorLockException;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class CuratorLockTest {

    private MockCurator curator;
    private CuratorLock curatorLock;

    @Before
    public void setupLock() {
        curator = new MockCurator();
        curatorLock = new CuratorLock(curator, "/foo");
    }

    @Test
    public void testAcquireNormal() {
        curator.timeoutOnLock = false;
        curator.throwExceptionOnLock = false;
        curatorLock.lock();
        curatorLock.unlock();
    }

    @Test
    public void testLockTimeout() throws InterruptedException {
        curator.timeoutOnLock = true;
        assertFalse(curatorLock.tryLock(0, TimeUnit.MILLISECONDS));
        curatorLock.unlock();
    }

    @Test(expected = CuratorLockException.class)
    public void testLockError() {
        curator.throwExceptionOnLock = true;
        curatorLock.lock();
        curatorLock.unlock();
    }

}
