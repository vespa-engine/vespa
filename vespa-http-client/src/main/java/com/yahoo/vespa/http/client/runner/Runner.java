// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.runner;

import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.FeedClientFactory;
import com.yahoo.vespa.http.client.SimpleLoggerResultCallback;
import com.yahoo.vespa.http.client.core.JsonReader;
import com.yahoo.vespa.http.client.core.XmlFeedReader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @author dybdahl
 * @since 5.1.20
 */
public class Runner {
    /**
     * Feed data from inputFile to session.
     * @param feedClient where to send data to
     * @param inputStream source of data
     * @param isJson if input stream is of json formatted data
     * @param numSent is updated while sending by this method
     * @param verbose if true will print some information to stderr
     * @return send time in ms, not including validating
     */
    public static long send(
            FeedClient feedClient, InputStream inputStream, boolean isJson,
            AtomicInteger numSent, boolean verbose, boolean addRootElementToXml) {

        if (verbose) {
            System.err.println("Now sending data.");
        }
        long sendStartTime = System.currentTimeMillis();
        if (isJson) {
            JsonReader.read(inputStream, feedClient, numSent);
        } else {
            try {
                XmlFeedReader.read(
                        addRootElementToXml ? addVespafeedTag(inputStream) : inputStream, feedClient, numSent);
            } catch (Exception e) {
                System.err.println("Stopped reading feed, got problems with XML: " + e.getMessage());
            }
        }

        long sendTotalTime = System.currentTimeMillis() - sendStartTime;

        if (verbose) {
            System.err.println("Waiting for all results, sent " + numSent.get() + " docs.");
        }
        feedClient.close();
        if (verbose) {
            System.err.println("Session closed.");
        }
        return sendTotalTime;
    }

    // public for testing.
    public static InputStream addVespafeedTag(InputStream inputStream) {
        return new SequenceInputStream(Collections.enumeration(Arrays.asList(
                new InputStream[]{
                        new ByteArrayInputStream("<vespafeed>".getBytes()),
                        inputStream,
                        new ByteArrayInputStream("</vespafeed>".getBytes()),
                }))
        );
    }

    public static void main(String[] args) throws FileNotFoundException, InterruptedException {
        final CommandLineArguments commandLineArgs = CommandLineArguments.build(args);
        if (commandLineArgs == null) {
            return;
        }
        // TODO: Rather implement this by peeking the stream if possible.
        final boolean useJson = commandLineArgs.getFile().endsWith(".json");

        final AtomicInteger numSent = new AtomicInteger(0);
        InputStream inputStream = new FileInputStream(commandLineArgs.getFile());

        int intervalOfLogging = commandLineArgs.getVerbose()
                ? commandLineArgs.getWhenVerboseEnabledPrintMessageForEveryXDocuments()
                : Integer.MAX_VALUE;
        final SimpleLoggerResultCallback callback = new SimpleLoggerResultCallback(numSent, intervalOfLogging);

        final FeedClient feedClient = FeedClientFactory.create(commandLineArgs.createSessionParams(useJson), callback);

        long sendTotalTimeMs = send(
                feedClient, inputStream, useJson, numSent, commandLineArgs.getVerbose(),
                commandLineArgs.getAddRootElementToXml());

        if (commandLineArgs.getVerbose()) {
            System.err.println(feedClient.getStatsAsJson());
            double fileSizeMb = ((double) new File(commandLineArgs.getFile()).length()) / 1024.0 / 1024.0;
            double transferTimeSec = ((double) sendTotalTimeMs) / 1000.0;
            System.err.println("Sent " + fileSizeMb + " MB in " + transferTimeSec + " seconds.");
            System.err.println("Speed: " + ((fileSizeMb / transferTimeSec) * 8.0) + " Mbits/sec, + HTTP overhead " +
                    "(not taking  compression into account)");
            if (transferTimeSec > 0) {
                System.err.printf("Docs/sec %.3f%n\n", numSent.get() / transferTimeSec);
            }
        }
        callback.printProgress();
    }
}
