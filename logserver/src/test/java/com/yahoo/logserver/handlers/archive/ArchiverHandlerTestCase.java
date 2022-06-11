// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.archive;

import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;
import com.yahoo.plugin.SystemPropertyConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Bjorn Borud
 */
public class ArchiverHandlerTestCase {

    private static final String[] mStrings = {
        "1095159244.095000\thost\t1/2\tservice\tcomponent\tinfo\tpayload1",
        "1095206399.000000\thost\t1/2\tservice\tcomponent\tinfo\tpayload2",
        "1095206400.000000\thost\t1/2\tservice\tcomponent\tinfo\tpayload3",
        "1095206401.000000\thost\t1/2\tservice\tcomponent\tinfo\tpayload4",
    };

    private static final LogMessage[] msg = new LogMessage[mStrings.length];

    static {
        try {
            for (int i = 0; i < mStrings.length; i++) {
                msg[i] = LogMessage.parseNativeFormat(mStrings[i]);
            }
        } catch (InvalidLogFormatException e) {
            throw new RuntimeException(e);
        }

        // mute the logging
        Logger.getLogger(ArchiverHandler.class.getName()).setLevel(Level.WARNING);
        Logger.getLogger(LogWriter.class.getName()).setLevel(Level.WARNING);

    }


    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Make sure that rollover for dateHash() occurs at midnight, so that
     * points in time belonging to the same day are in the interval
     * [00:00:00.000, 23:59:59.999].
     */
    @Test
    public void testDateHash() throws IOException {
        File tmpDir = temporaryFolder.newFolder();

        ArchiverHandler a = new ArchiverHandler(tmpDir.getAbsolutePath(),
                                                1024, "gzip");
        long now = 1095159244095L;
        long midnight = 1095206400000L;
        assertEquals(2004091410, a.dateHash(now));
        assertEquals(2004091423, a.dateHash(midnight - 1));
        assertEquals(2004091500, a.dateHash(midnight));
        assertEquals(2004091500, a.dateHash(midnight + 1));
        a.close();
    }

    /**
     * Test that the ArchiverHandler creates correct filenames.
     */
    @Test
    public void testFilenameCreation() throws IOException {
        File tmpDir = temporaryFolder.newFolder();
        try {
            ArchiverHandler a = new ArchiverHandler(tmpDir.getAbsolutePath(),
                                                    1024, "gzip");
            LogMessage msg1 = LogMessage.parseNativeFormat("1139322725\thost\t1/1\tservice\tcomponent\tinfo\tpayload");
            LogMessage msg2 = LogMessage.parseNativeFormat("1161172200\thost\t1/1\tservice\tcomponent\tinfo\tpayload");
            assertEquals(tmpDir.getAbsolutePath() + "/2006/02/07/14", a.getPrefix(msg1));
            assertEquals(tmpDir.getAbsolutePath() + "/2006/10/18/11", a.getPrefix(msg2));
            assertEquals(a.getPrefix(msg1).length(), a.getPrefix(msg2).length());
            a.close();
        } catch (InvalidLogFormatException e) {
            fail(e.toString());
        }
    }

    /**
     * Log some messages to the handler and verify that they are
     * written.
     */
    @Test
    public void testLogging() throws java.io.IOException, InvalidLogFormatException {
        File tmpDir = temporaryFolder.newFolder();

        ArchiverHandler a = new ArchiverHandler(tmpDir.getAbsolutePath(),
                                                1024, "gzip");

        for (int i = 0; i < msg.length; i++) {
            a.handle(msg[i]);
        }
        a.close();


        // make sure all the log files are there, that all log entries
        // are accounted for and that they match what we logged.
        int messageCount = 0;

        // map of files we've already inspected.  this we need to ensure
        // that we are not inspecting the same files over and over again
        // so the counts get messed up
        Set<String> inspectedFiles = new HashSet<String>();

        for (int i = 0; i < msg.length; i++) {
            String name = a.getPrefix(msg[i]) + "-0";

            // have we inspected this file before?
            if (! inspectedFiles.add(name)) {
                continue;
            }


            File f = new File(name);
            assertTrue(f.exists());

            BufferedReader br = new BufferedReader(new FileReader(f));
            for (String line = br.readLine();
                 line != null;
                 line = br.readLine()) {
                // primitive check if the messages match
                boolean foundMatch = false;
                for (int k = 0; k < mStrings.length; k++) {
                    if (mStrings[k].equals(line)) {
                        foundMatch = true;
                        break;
                    }
                }
                assertTrue(foundMatch);

                // try to instantiate messages to ensure that they
                // are parseable
                @SuppressWarnings("unused")
                    LogMessage m = LogMessage.parseNativeFormat(line);
                messageCount++;
            }
            br.close();
        }

        // verify that the number of log messages written equals
        // the number of log messages we have in our test
        assertEquals(mStrings.length, messageCount);
    }

    /**
     * Make sure that the file is rotated after N bytes
     */
    @Test
    public void testRotation() throws IOException {
        File tmpDir = temporaryFolder.newFolder();

        ArchiverHandler a = new ArchiverHandler(tmpDir.getAbsolutePath(),
                                                msg[1].toString().length() + 1,
                                                "gzip");
        // log the same message 4 times
        for (int i = 0; i < 4; i++) {
            a.handle(msg[1]);
        }
        a.close();

        // we should now have 3 files
        String prefix = a.getPrefix(msg[1]);
        int msgCount = 0;
        for (int i = 0; i < 3; i++) {
            File f = new File(prefix + "-" + i);
            assertTrue(f.exists());

            // ensure there's the same log message in all files
            BufferedReader br = new BufferedReader(new FileReader(f));
            for (String line = br.readLine();
                 line != null;
                 line = br.readLine()) {
                assertTrue(msg[1].toString().equals((line + "\n")));
                msgCount++;
            }
            br.close();
        }
        assertEquals(4, msgCount);

        // make sure we have no more than 3 files
        assertTrue(! (new File(prefix + "-3").exists()));



    }

    @Test
    public void testCacheEldestEntry() throws IOException {
        LogWriterLRUCache cache = new LogWriterLRUCache(5, (float) 0.75);
        String d = "target/tmp/logarchive";
        FilesArchived archive = new FilesArchived(new File(d), "gzip");
        for (int i = 0; i < cache.maxEntries + 10; i++) {
            cache.put(i, new LogWriter(d+"/2018/12/31/17", 5, archive));
        }
        assertEquals(cache.size(), cache.maxEntries);
    }

    @Test
    public void testArchiverPlugin() {
        ArchiverPlugin ap = new ArchiverPlugin();
        try {
            ap.shutdownPlugin();
            fail("Shutdown before init didn't throw.");
        } catch (Exception e) {
        }
        ap.initPlugin(new SystemPropertyConfig("test"));
        try {
            ap.initPlugin(new SystemPropertyConfig("test"));
            fail("Multiple init didn't throw.");
        } catch (Exception e) {
        }
        ap.shutdownPlugin();
    }

}
