// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.document.serialization.DeserializationException;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.messagebus.DynamicThrottlePolicy;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.ThrottlePolicy;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleFeederTest {

    private static final String CONFIG_DIR = "target/test-classes/";

    @Test
    public void requireThatFeederWorks() throws Throwable {
        assertFeed("<vespafeed>" +
                   "    <document documenttype='simple' documentid='doc:scheme:0'>" +
                   "        <my_str>foo</my_str>" +
                   "    </document>" +
                   "    <update documenttype='simple' documentid='doc:scheme:1'>" +
                   "        <assign field='my_str'>bar</assign>" +
                   "    </update>" +
                   "    <remove documenttype='simple' documentid='doc:scheme:2'/>" +
                   "</vespafeed>",
                   new MessageHandler() {

                       @Override
                       public void handleMessage(Message msg) {
                           Reply reply = ((DocumentMessage)msg).createReply();
                           reply.swapState(msg);
                           reply.popHandler().handleReply(reply);
                       }
                   },
                   "",
                   "(.+\n)+" +
                   "\\s*\\d+,\\s*3,.+\n");
    }

    @Test
    public void requireThatParseFailuresThrowInMainThread() throws Throwable {
        TestDriver driver = new TestDriver(new FeederParams(),
                                           "<vespafeed>" +
                                           "    <document documenttype='unknown' documentid='doc:scheme:0'/>" +
                                           "</vespafeed>",
                                           null);
        try {
            driver.run();
            fail();
        } catch (DeserializationException e) {
            assertEquals("Field 'doc:scheme:0': Must specify an existing document type, not 'unknown' (at line 1, column 76)",
                         e.getMessage());
        }
        assertTrue(driver.close());
    }

    @Test
    public void requireThatSyncFailuresThrowInMainThread() throws Throwable {
        TestDriver driver = new TestDriver(new FeederParams(),
                                           "<vespafeed>" +
                                           "    <document documenttype='simple' documentid='doc:scheme:0'/>" +
                                           "</vespafeed>",
                                           null);
        getSourceSession(driver).close();
        try {
            driver.run();
            fail();
        } catch (IOException e) {
            assertEquals("[SEND_QUEUE_CLOSED @ localhost]: Source session is closed.", e.getMessage());
        }
        assertTrue(driver.close());
    }

    @Test
    public void requireThatAsyncFailuresThrowInMainThread() throws Throwable {
        TestDriver driver = new TestDriver(new FeederParams(),
                                           "<vespafeed><document documenttype='simple' documentid='doc:scheme:0'/></vespafeed>",
                                           new MessageHandler() {

                                               @Override
                                               public void handleMessage(Message msg) {
                                                   Reply reply = new EmptyReply();
                                                   reply.swapState(msg);
                                                   reply.addError(new Error(ErrorCode.APP_FATAL_ERROR + 6, "foo"));
                                                   reply.addError(new Error(ErrorCode.APP_FATAL_ERROR + 9, "bar"));
                                                   reply.popHandler().handleReply(reply);
                                               }
                                           });
        try {
            driver.run();
            fail();
        } catch (IOException e) {
            assertMatches("com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage@.+\n" +
                          "\\[UNKNOWN\\(250006\\) @ .+\\]: foo\n" +
                          "\\[UNKNOWN\\(250009\\) @ .+\\]: bar\n",
                          e.getMessage());
        }
        assertTrue(driver.close());
    }

    @Test
    public void requireThatDynamicThrottlingIsDefault() throws Exception {
        TestDriver driver = new TestDriver(new FeederParams(), "", null);
        assertEquals(DynamicThrottlePolicy.class, getThrottlePolicy(driver).getClass());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatSerialTransferModeConfiguresStaticThrottling() throws Exception {
        TestDriver driver = new TestDriver(new FeederParams().setSerialTransfer(true), "", null);
        assertEquals(StaticThrottlePolicy.class, getThrottlePolicy(driver).getClass());
        assertTrue(driver.close());
    }

    private static SourceSession getSourceSession(TestDriver driver) {
        return (SourceSession)getField(driver.feeder, "session");
    }

    private static ThrottlePolicy getThrottlePolicy(TestDriver driver) {
        return (ThrottlePolicy)getField(getSourceSession(driver), "throttlePolicy");
    }

    private static Object getField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertFeed(String in, MessageHandler validator, String expectedErr, String expectedOut)
            throws Throwable {
        TestDriver driver = new TestDriver(new FeederParams(), in, validator);
        driver.run();
        assertMatches(expectedErr, new String(driver.err.toByteArray(), StandardCharsets.UTF_8));
        assertMatches(expectedOut, new String(driver.out.toByteArray(), StandardCharsets.UTF_8));
        assertTrue(driver.close());
    }

    private static void assertMatches(String expected, String actual) {
        if (!Pattern.matches(expected, actual)) {
            assertEquals(expected, actual);
        }
    }

    private static class TestDriver {

        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SimpleFeeder feeder;
        final SimpleServer server;

        public TestDriver(FeederParams params, String in, MessageHandler validator)
                throws IOException, ListenFailedException {
            server = new SimpleServer(CONFIG_DIR, validator);
            feeder = new SimpleFeeder(params.setConfigId("dir:" + CONFIG_DIR)
                                            .setStdErr(new PrintStream(err))
                                            .setStdIn(new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)))
                                            .setStdOut(new PrintStream(out)));
        }

        void run() throws Throwable {
            feeder.run();
        }

        boolean close() {
            feeder.close();
            server.close();
            return true;
        }
    }

}
