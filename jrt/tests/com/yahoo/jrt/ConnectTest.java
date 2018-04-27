// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import static org.junit.Assert.assertTrue;

public class ConnectTest {

    @org.junit.Test
    public void testConnect() throws ListenFailedException {
        Test.Orb server   = new Test.Orb(new Transport());
        Test.Orb client   = new Test.Orb(new Transport());
        Acceptor acceptor = server.listen(new Spec(Test.PORT));

        assertTrue(server.checkLifeCounts(0, 0));
        assertTrue(client.checkLifeCounts(0, 0));

        Target target = client.connect(new Spec("localhost", Test.PORT));

        for (int i = 0; i < 100; i++) {
            if (client.initCount == 1 && server.initCount == 1) {
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }

        assertTrue(server.checkLifeCounts(1, 0));
        assertTrue(client.checkLifeCounts(1, 0));

        target.close();

        for (int i = 0; i < 100; i++) {
            if (client.finiCount == 1 && server.finiCount == 1) {
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }

        assertTrue(server.checkLifeCounts(1, 1));
        assertTrue(client.checkLifeCounts(1, 1));

        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

}
