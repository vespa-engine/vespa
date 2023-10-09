// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.ThrottlePolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class SimpleFeederTest {

    private static final String CONFIG_DIR = "target/test-classes/";

    @Test
    public void requireThatXMLFeederWorks() throws Throwable {
        assertFeed("<vespafeed>" +
                   "    <document documenttype='simple' documentid='id:scheme:simple::0'>" +
                   "        <my_str>foo</my_str>" +
                   "    </document>" +
                   "    <update documenttype='simple' documentid='id:scheme:simple::1'>" +
                   "        <assign field='my_str'>bar</assign>" +
                   "    </update>" +
                   "    <remove documenttype='simple' documentid='id:scheme:simple::2'/>" +
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
    public void requireThatXML2JsonFeederWorks() throws Throwable {
        ByteArrayOutputStream dump = new ByteArrayOutputStream();
        assertFeed(new FeederParams().setDumpStream(dump),
                "<vespafeed>" +
                        "    <document documenttype='simple' documentid='id:simple:simple::0'>" +
                        "        <my_str>foo</my_str>" +
                        "    </document>" +
                        "    <update documenttype='simple' documentid='id:simple:simple::1'>" +
                        "        <assign field='my_str'>bar</assign>" +
                        "    </update>" +
                        "    <remove documenttype='simple' documentid='id:simple:simple::2'/>" +
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
        assertEquals(58, dump.size());
        assertEquals("[\n{\"id\":\"id:simple:simple::0\",\"fields\":{\"my_str\":\"foo\"}}\n]", dump.toString());
    }

    @Test
    public void requireThatDualPutXML2JsonFeederWorks() throws Throwable {
        ByteArrayOutputStream dump = new ByteArrayOutputStream();
        assertFeed(new FeederParams().setDumpStream(dump),
                "<vespafeed>" +
                        "    <document documenttype='simple' documentid='id:simple:simple::0'>" +
                        "        <my_str>foo</my_str>" +
                        "    </document>" +
                        "    <document documenttype='simple' documentid='id:simple:simple::1'>" +
                        "        <my_str>bar</my_str>" +
                        "    </document>" +
                        "    <remove documenttype='simple' documentid='id:simple:simple::2'/>" +
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
        assertEquals(115, dump.size());
        assertEquals("[\n{\"id\":\"id:simple:simple::0\",\"fields\":{\"my_str\":\"foo\"}},\n {\"id\":\"id:simple:simple::1\",\"fields\":{\"my_str\":\"bar\"}}\n]", dump.toString());
        assertFeed(dump.toString(),
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
                        "\\s*\\d+,\\s*2,.+\n");
    }

    @Test
    public void requireThatJson2VespaFeederWorks() throws Throwable {
        ByteArrayOutputStream dump = new ByteArrayOutputStream();
        assertFeed(new FeederParams().setDumpStream(dump).setDumpFormat(FeederParams.DumpFormat.VESPA),
                "[" +
                        "  { \"put\": \"id:simple:simple::0\", \"fields\": { \"my_str\":\"foo\"}}," +
                        "  { \"update\": \"id:simple:simple::1\", \"fields\": { \"my_str\": { \"assign\":\"bar\"}}}," +
                        "  { \"remove\": \"id:simple:simple::2\", \"condition\":\"true\"}" +
                        "]",
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
        assertEquals(187, dump.size());
        assertFeed(new ByteArrayInputStream(dump.toByteArray()),
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
    public void requireThatJsonFeederWorks() throws Throwable {
        assertFeed("[" +
                        "  { \"put\": \"id:simple:simple::0\", \"fields\": { \"my_str\":\"foo\"}}," +
                        "  { \"update\": \"id:simple:simple::1\", \"fields\": { \"my_str\": { \"assign\":\"bar\"}}}," +
                        "  { \"remove\": \"id:simple:simple::2\"}" +
                        "]",
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
                                           "    <document documenttype='unknown' documentid='id:scheme:simple::0'/>" +
                                           "</vespafeed>",
                                           null);
        try {
            driver.run();
            fail();
        } catch (DeserializationException e) {
            assertEquals("Field 'id:scheme:simple::0': Must specify an existing document type, not 'unknown' (at line 1, column 83)",
                         e.getMessage());
        }
        assertTrue(driver.close());
    }

    @Test
    public void requireThatSyncFailuresThrowInMainThread() throws Throwable {
        TestDriver driver = new TestDriver(new FeederParams(),
                                           "<vespafeed>" +
                                           "    <document documenttype='simple' documentid='id:scheme:simple::0'/>" +
                                           "</vespafeed>",
                                           null);
        driver.feeder.getSourceSession().close();
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
                                           "<vespafeed><document documenttype='simple' documentid='id:scheme:simple::0'/></vespafeed>",
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
        TestDriver driver = new TestDriver(new FeederParams().setSerialTransfer(), "", null);
        assertEquals(StaticThrottlePolicy.class, getThrottlePolicy(driver).getClass());
        assertTrue(driver.close());
    }

    private static ThrottlePolicy getThrottlePolicy(TestDriver driver) {
        return (ThrottlePolicy)getField(driver.feeder.getSourceSession(), "throttlePolicy");
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

    private static void assertFeed(String in, MessageHandler validator, String expectedErr, String expectedOut) throws Throwable {
        assertFeed(new FeederParams(), in, validator, expectedErr, expectedOut);
    }
    private static void assertFeed(InputStream in, MessageHandler validator, String expectedErr, String expectedOut) throws Throwable {
        assertFeed(new FeederParams(), in, validator, expectedErr, expectedOut);
    }
    private static void assertFeed(FeederParams params, String in, MessageHandler validator, String expectedErr, String expectedOut) throws Throwable {
        assertFeed(params, new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)), validator, expectedErr, expectedOut);
    }
    private static void assertFeed(FeederParams params, InputStream in, MessageHandler validator, String expectedErr, String expectedOut) throws Throwable {
        TestDriver driver = new TestDriver(params, in, validator);
        driver.run();
        assertMatches(expectedErr, driver.err.toString(StandardCharsets.UTF_8));
        assertMatches(expectedOut, driver.out.toString(StandardCharsets.UTF_8));
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

        TestDriver(FeederParams params, String in, MessageHandler validator) throws IOException, ListenFailedException {
            this(params, new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)), validator);
        }
        TestDriver(FeederParams params, InputStream in, MessageHandler validator) throws IOException, ListenFailedException {
            server = new SimpleServer(CONFIG_DIR, validator);
            feeder = new SimpleFeeder(params.setConfigId("dir:" + CONFIG_DIR)
                                            .setStdErr(new PrintStream(err))
                                            .setInputStreams(List.of(in))
                                            .setStdOut(new PrintStream(out)));
        }

        void run() throws Throwable {
            feeder.run();
        }

        boolean close() throws Exception {
            feeder.close();
            server.close();
            return true;
        }
    }

}
