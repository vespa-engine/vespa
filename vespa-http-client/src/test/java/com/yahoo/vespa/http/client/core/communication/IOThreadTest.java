// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.core.OperationProcessorTester;
import com.yahoo.vespa.http.client.core.ServerResponseException;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * TODO: Migrate IOThreadTests here.
 *
 * @author bratseth
 */
public class IOThreadTest {

    @Test
    public void testSuccessfulWriting() {
        OperationProcessorTester tester = new OperationProcessorTester();
        assertEquals(0, tester.incomplete());
        assertEquals(0, tester.success());
        assertEquals(0, tester.failures());
        tester.send("doc1");
        tester.send("doc2");
        tester.send("doc3");
        assertEquals(3, tester.incomplete());
        assertEquals(0, tester.success());
        assertEquals(0, tester.failures());
        tester.tick(1); // connect
        assertEquals(3, tester.incomplete());
        tester.tick(1); // sync
        assertEquals(3, tester.incomplete());
        tester.tick(1); // process queue
        assertEquals(0, tester.incomplete());
        assertEquals(3, tester.success());
        assertEquals(0, tester.failures());
    }

    @Test
    public void testFatalExceptionOnHandshake() {
        OperationProcessorTester tester = new OperationProcessorTester();
        IOThread ioThread = tester.getSingleIOThread();
        DryRunGatewayConnection firstConnection = (DryRunGatewayConnection)ioThread.currentConnection();
        firstConnection.throwOnHandshake(new ServerResponseException(403, "Not authorized"));

        tester.send("doc1");
        tester.send("doc2");
        tester.send("doc3");
        tester.tick(3);
        assertEquals(0, tester.incomplete());
        assertEquals(0, ioThread.resultQueue().getPendingSize());
        assertEquals(0, tester.success());
        assertEquals(3, tester.failures());
    }

    @Test
    public void testExceptionOnHandshake() {
        OperationProcessorTester tester = new OperationProcessorTester();
        IOThread ioThread = tester.getSingleIOThread();
        DryRunGatewayConnection firstConnection = (DryRunGatewayConnection)ioThread.currentConnection();
        firstConnection.throwOnHandshake(new ServerResponseException(418, "I'm a teapot"));

        tester.send("doc1");
        tester.tick(3);
        assertEquals(1, tester.incomplete());
        assertEquals(0, ioThread.resultQueue().getPendingSize());
        assertEquals(0, tester.success());
        assertEquals("Awaiting retry", 0, tester.failures());
    }

    @Test
    public void testExceptionOnWrite() {
        OperationProcessorTester tester = new OperationProcessorTester();
        IOThread ioThread = tester.getSingleIOThread();
        DryRunGatewayConnection firstConnection = (DryRunGatewayConnection)ioThread.currentConnection();
        firstConnection.throwOnWrite(new IOException("Test failure"));

        tester.send("doc1");
        tester.tick(3);
        assertEquals(1, tester.incomplete());
        assertEquals(0, ioThread.resultQueue().getPendingSize());
        assertEquals(0, tester.success());
        assertEquals("Awaiting retry since write exceptions is a transient failure",
                     0, tester.failures());
    }

    @Test
    public void testPollingOldConnections() {
        OperationProcessorTester tester = new OperationProcessorTester();
        tester.tick(3);

        IOThread ioThread = tester.getSingleIOThread();
        DryRunGatewayConnection firstConnection = (DryRunGatewayConnection)ioThread.currentConnection();
        assertEquals(0, ioThread.oldConnections().size());

        firstConnection.hold(true);
        tester.send("doc1");
        tester.tick(1);

        tester.clock().advance(Duration.ofSeconds(31)); // Default connection ttl is 30
        tester.tick(3);

        assertEquals(1, ioThread.oldConnections().size());
        assertEquals(firstConnection, ioThread.oldConnections().get(0));
        assertNotSame(firstConnection, ioThread.currentConnection());
        assertEquals(31, firstConnection.lastPollTime().toEpochMilli() / 1000);

        // Check old connection poll pattern (exponential backoff)
        assertLastPollTimeWhenAdvancing(31, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(33, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(33, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(33, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(33, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(37, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(45, 1, firstConnection, tester);

        tester.clock().advance(Duration.ofSeconds(200));
        tester.tick(1);
        assertEquals("Old connection is eventually removed", 0, ioThread.oldConnections().size());
    }

    @Test
    public void old_connections_are_closed_early_if_no_inflight_operations() {
        OperationProcessorTester tester = new OperationProcessorTester();
        tester.tick(3);

        IOThread ioThread = tester.getSingleIOThread();
        DryRunGatewayConnection firstConnection = (DryRunGatewayConnection)ioThread.currentConnection();
        assertEquals(0, ioThread.oldConnections().size());

        firstConnection.hold(true); // do not send result for doc1 in next http response
        tester.send("doc1");
        tester.tick(1);
        tester.clock().advance(Duration.ofSeconds(31)); // Default connection TTL + 1
        tester.tick(1);
        assertEquals(1, ioThread.oldConnections().size());

        firstConnection.hold(false); // send result for both doc1 and doc2 in next http response
        tester.send("doc2");
        tester.tick(1);
        assertEquals(1, ioThread.oldConnections().size());
        tester.clock().advance(Duration.ofSeconds(2));
        tester.tick(3);
        assertLastPollTimeWhenAdvancing(33, 1, firstConnection, tester);
        assertEquals(0, ioThread.oldConnections().size());
    }

    private void assertLastPollTimeWhenAdvancing(int lastPollTimeSeconds,
                                                 int advanceSeconds,
                                                 DryRunGatewayConnection connection,
                                                 OperationProcessorTester tester) {
        tester.clock().advance(Duration.ofSeconds(advanceSeconds));
        tester.tick(1);
        assertEquals(lastPollTimeSeconds, connection.lastPollTime().toEpochMilli() / 1000);
    }

}
