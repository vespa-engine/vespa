// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.testutils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import com.yahoo.log.LogLevel;
import com.yahoo.log.event.MalformedEventException;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;

/**
 * This utility is used to check that the log messages contained
 * in a log file are correct.  Any incorrectly formatted log
 * message is output to stdout.
 *
 * @author  Bjorn Borud
 */
public class VerifyLogfile {

    public static void main (String[] args) throws IOException {
        int messages = 0;
        int events = 0;
        int invalidLogMessages = 0;
        int invalidEvents = 0;
        int numFiles = 0;

        if (args.length < 1) {
            System.err.println("\nPlease provide name of log file(s)\n");
        }

        for (int i = 0; i < args.length; i++) {
            BufferedReader br = new BufferedReader(new FileReader(args[i]));
            numFiles++;
            for (String line = br.readLine();
                 line != null;
                 line = br.readLine())
            {
                messages++;
                LogMessage m;
                try {
                    m = LogMessage.parseNativeFormat(line);
                    if (m.getLevel() == LogLevel.EVENT) {
                        events++;
                        m.getEvent();
                    }
                } catch (MalformedEventException e) {
                    System.out.println("EVENT\t" + line);
                    invalidEvents++;
                } catch (InvalidLogFormatException e) {
                    System.out.println("MESSAGE\t" + line);
                    invalidLogMessages++;
                }
            }
            br.close();
        }

        System.err.println("numFiles: " + numFiles);
        System.err.println("messages: " + messages);
        System.err.println("events: " + events);
        System.err.println("invalidLogMessages: " + invalidLogMessages);
        System.err.println("invalidEvents: " + invalidEvents);
    }
}
