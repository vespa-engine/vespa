// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectTest {

    @org.junit.Test
    public void testConnect() throws ListenFailedException {
        Test.Orb server   = new Test.Orb(new Transport());
        Test.Orb client   = new Test.Orb(new Transport());
        Acceptor acceptor = server.listen(new Spec(0));

        Target target = client.connect(new Spec("localhost", acceptor.port()));

        for (int i = 0; i < 100; i++) {
            if (target.isValid()) {
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        assertTrue(target.isValid());
        target.close();
        for (int i = 0; i < 100; i++) {
            if (!target.isValid()) {
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        assertFalse(target.isValid());
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

}
