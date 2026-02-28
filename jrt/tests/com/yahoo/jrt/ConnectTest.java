// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectTest {

    @org.junit.Test
    public void testConnect() throws ListenFailedException, java.net.SocketException {
        Test.Orb server   = new Test.Orb(new Transport());
        Test.Orb client   = new Test.Orb(new Transport());
        Acceptor acceptor = server.listen(new Spec(0));

        Connection target = (Connection) client.connect(new Spec("localhost", acceptor.port()));

        for (int i = 0; i < 100; i++) {
            if (target.isConnected()) {
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        assertTrue(target.isConnected());

        // Verify socket options set in Connection.init()
        java.net.Socket sock = target.socketChannel().socket();
        assertTrue("SO_KEEPALIVE should be enabled", sock.getKeepAlive());
        assertEquals("SO_LINGER should be 0", 0, sock.getSoLinger());
        assertTrue("TCP_NODELAY should be enabled", sock.getTcpNoDelay());

        target.close();

        for (int i = 0; i < 100; i++) {
            if (target.isClosed()) {
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        assertTrue(target.isClosed());

        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

}
