// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.vespa.curator.mock.MockCurator;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class CuratorCounterTest {

    @Test
    public void testCounter() throws Exception {
        DistributedAtomicLong counter = new MockCurator().createAtomicCounter("/mycounter");
        counter.initialize(4L);
        assertEquals(4L, counter.get().postValue().longValue());
        assertEquals(5L, counter.increment().postValue().longValue());
    }

}
