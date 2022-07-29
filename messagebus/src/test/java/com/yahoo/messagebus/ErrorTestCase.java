// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ErrorTestCase {

    @Test
    void requireThatAccessorsWork() {
        Error err = new Error(69, "foo");
        assertEquals(69, err.getCode());
        assertEquals("foo", err.getMessage());

        assertFalse(new Error(ErrorCode.TRANSIENT_ERROR, "foo").isFatal());
        assertFalse(new Error(ErrorCode.TRANSIENT_ERROR + 1, "foo").isFatal());
        assertTrue(new Error(ErrorCode.FATAL_ERROR, "foo").isFatal());
        assertTrue(new Error(ErrorCode.FATAL_ERROR + 1, "foo").isFatal());
    }

    @Test
    void requireThatErrorIsPropagated() throws Exception {
        RoutingTableSpec table = new RoutingTableSpec(SimpleProtocol.NAME);
        table.addHop("itr", "test/itr/session", Arrays.asList("test/itr/session"));
        table.addHop("dst", "test/dst/session", Arrays.asList("test/dst/session"));
        table.addRoute("test", Arrays.asList("itr", "dst"));

        Slobrok slobrok = new Slobrok();
        TestServer src = new TestServer("test/src", table, slobrok, null);
        TestServer itr = new TestServer("test/itr", table, slobrok, null);
        TestServer dst = new TestServer("test/dst", table, slobrok, null);

        Receptor ss_rr = new Receptor();
        SourceSession ss = src.mb.createSourceSession(ss_rr);

        Receptor is_mr = new Receptor();
        Receptor is_rr = new Receptor();
        IntermediateSession is = itr.mb.createIntermediateSession("session", true, is_mr, is_rr);

        Receptor ds_mr = new Receptor();
        DestinationSession ds = dst.mb.createDestinationSession("session", true, ds_mr);

        src.waitSlobrok("test/itr/session", 1);
        src.waitSlobrok("test/dst/session", 1);
        itr.waitSlobrok("test/dst/session", 1);

        for (int i = 0; i < 5; i++) {
            assertTrue(ss.send(new SimpleMessage("msg"), "test").isAccepted());
            Message msg = is_mr.getMessage(60);
            assertNotNull(msg);
            is.forward(msg);

            assertNotNull(msg = ds_mr.getMessage(60));
            Reply reply = new EmptyReply();
            msg.swapState(reply);
            reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, "fatality"));
            ds.reply(reply);

            assertNotNull(reply = is_rr.getReply(60));
            assertEquals(reply.getNumErrors(), 1);
            assertEquals(reply.getError(0).getService(), "test/dst/session");
            reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, "fatality"));
            is.forward(reply);

            assertNotNull(reply = ss_rr.getReply(60));
            assertEquals(reply.getNumErrors(), 2);
            assertEquals(reply.getError(0).getService(), "test/dst/session");
            assertEquals(reply.getError(1).getService(), "test/itr/session");
        }

        ss.destroy();
        is.destroy();
        ds.destroy();

        dst.destroy();
        itr.destroy();
        src.destroy();
        slobrok.stop();
    }

    @Test
    void testErrorCodeCategorization() {
        assertTrue(ErrorCode.isFatal(ErrorCode.FATAL_ERROR));
        assertFalse(ErrorCode.isTransient(ErrorCode.FATAL_ERROR));
        assertTrue(ErrorCode.isMBusError(ErrorCode.FATAL_ERROR));

        assertTrue(ErrorCode.isFatal(ErrorCode.APP_FATAL_ERROR));
        assertFalse(ErrorCode.isTransient(ErrorCode.APP_FATAL_ERROR));
        assertFalse(ErrorCode.isMBusError(ErrorCode.APP_FATAL_ERROR));


        assertFalse(ErrorCode.isFatal(ErrorCode.TRANSIENT_ERROR));
        assertTrue(ErrorCode.isTransient(ErrorCode.TRANSIENT_ERROR));
        assertTrue(ErrorCode.isMBusError(ErrorCode.TRANSIENT_ERROR));

        assertFalse(ErrorCode.isFatal(ErrorCode.APP_TRANSIENT_ERROR));
        assertTrue(ErrorCode.isTransient(ErrorCode.APP_TRANSIENT_ERROR));
        assertFalse(ErrorCode.isMBusError(ErrorCode.APP_TRANSIENT_ERROR));

        assertFalse(ErrorCode.isFatal(ErrorCode.APP_TRANSIENT_ERROR - 1));
        assertTrue(ErrorCode.isTransient(ErrorCode.APP_TRANSIENT_ERROR - 1));
        assertTrue(ErrorCode.isMBusError(ErrorCode.APP_TRANSIENT_ERROR - 1));

        assertFalse(ErrorCode.isFatal(ErrorCode.FATAL_ERROR - 1));
        assertTrue(ErrorCode.isTransient(ErrorCode.FATAL_ERROR - 1));
        assertFalse(ErrorCode.isMBusError(ErrorCode.FATAL_ERROR - 1));

        assertFalse(ErrorCode.isFatal(ErrorCode.TRANSIENT_ERROR - 1));
        assertFalse(ErrorCode.isTransient(ErrorCode.TRANSIENT_ERROR - 1));
        assertTrue(ErrorCode.isMBusError(ErrorCode.TRANSIENT_ERROR - 1));

        assertFalse(ErrorCode.isFatal(ErrorCode.NONE));
        assertFalse(ErrorCode.isTransient(ErrorCode.NONE));
        assertTrue(ErrorCode.isMBusError(ErrorCode.NONE));

    }
}
