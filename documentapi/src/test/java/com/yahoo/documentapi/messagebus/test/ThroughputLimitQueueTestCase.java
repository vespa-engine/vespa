// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.test;

import com.yahoo.documentapi.ThroughputLimitQueue;
import com.yahoo.concurrent.Timer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author thomasg
 */
public class ThroughputLimitQueueTestCase {

     class TestTimer implements Timer {
        public long milliTime = 0;

        public long milliTime() {
            return milliTime;
        }
    }

    @Test
    public void testCapacity() {
        TestTimer t = new TestTimer();
        t.milliTime = 10;
        ThroughputLimitQueue<Object> q = new ThroughputLimitQueue<Object>(t, 2000);

        q.add(new Object());
        q.add(new Object());
        q.remove();
        t.milliTime += 10;
        q.remove();

        assertEquals(200, q.capacity());

        for (int i = 0; i < 1000; i++) {
            q.add(new Object());
            q.add(new Object());
            t.milliTime += 100;
            q.remove();
            t.milliTime += 100;
            q.remove();
        }

        assertEquals(20, q.capacity());
    }

}
