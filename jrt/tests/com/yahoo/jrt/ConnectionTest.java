// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectionTest {

    @org.junit.Test
    public void closeSocketIsVisibleAcrossThreads() throws Exception {
        Test.Orb server = new Test.Orb(new Transport());
        Test.Orb client = new Test.Orb(new Transport());
        Acceptor acceptor = server.listen(new Spec(0));
        Target target = client.connect(new Spec("localhost", acceptor.port()));

        // Wait for connection to be established
        for (int i = 0; i < 100 && !target.isValid(); i++) {
            Thread.sleep(10);
        }
        assertTrue(target.isValid());

        // Close the socket from a different thread
        CountDownLatch done = new CountDownLatch(1);
        boolean[] closed = {false};
        new Thread(() -> {
            target.closeSocket();
            closed[0] = true;
            done.countDown();
        }).start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(closed[0]);

        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

}
