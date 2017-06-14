// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.test;

import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is used to gain access to a bunch of log entries
 * so we can use the same log messages in several different tests
 *
 * @author Bjorn Borud
 */
public class MockLogEntries {
    private static final MockLogEntries instance = new MockLogEntries();

    private final LogMessage[] messages;

    /**
     * Private constructor which reads the log messages and builds
     * an array of LogMessage entries.
     */
    private MockLogEntries() {
        List<LogMessage> msgs = new LinkedList<LogMessage>();
        try {
            String name = "src/test/files/logEntries.txt";
            BufferedReader br = new BufferedReader(new FileReader(name));
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                LogMessage m = LogMessage.parseNativeFormat(line);
                msgs.add(m);
            }
        } catch (InvalidLogFormatException | IOException e) {
            // do nothing
        }

        LogMessage[] m = new LogMessage[msgs.size()];
        msgs.toArray(m);
        messages = m;
    }

    /**
     * Return the LogMessage instances we've constructed from the
     * stored log messages we have.
     */
    public static LogMessage[] getMessages() {
        return instance.messages;
    }
}
