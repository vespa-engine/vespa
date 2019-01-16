// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.core.Document;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CloseableQTestCase {
    @Test
    public void requestThatPutIsInterruptedOnClose() throws InterruptedException {
        final DocumentQueue q = new DocumentQueue(1);
        q.put(new Document("id", null, "data", null), false);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {

                }
                q.close();
                q.clear();
            }
        });
        t.start();
        try {
            q.put(new Document("id2", null, "data2", null), false);
            fail("This shouldn't have worked.");
        } catch (IllegalStateException ise) {
            // ok!
        }
        try {
            t.join();
        } catch (InterruptedException e) {
        }
    }

    @Test
    public void requireThatSelfIsUnbounded() throws InterruptedException {
        DocumentQueue q = new DocumentQueue(1);
        q.put(new Document("1", null, "data", null), true);
        q.put(new Document("2", null, "data", null), true);
        q.put(new Document("3", null, "data", null), true);
        assertEquals(3, q.size());
    }
}
