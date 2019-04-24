// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.messagebus.routing.Route;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author Simon Thoresen Hult
 */
class FeederParams {

    private InputStream stdIn = System.in;
    private PrintStream stdErr = System.err;
    private PrintStream stdOut = System.out;
    private Route route = Route.parse("default");
    private String configId = "client";
    private OutputStream dumpStream = null;
    private boolean serialTransferEnabled = false;
    private int numDispatchThreads = 1;

    InputStream getStdIn() {
        return stdIn;
    }

    FeederParams setStdIn(InputStream stdIn) {
        this.stdIn = stdIn;
        return this;
    }

    PrintStream getStdErr() {
        return stdErr;
    }

    FeederParams setStdErr(PrintStream stdErr) {
        this.stdErr = stdErr;
        return this;
    }

    PrintStream getStdOut() {
        return stdOut;
    }

    FeederParams setStdOut(PrintStream stdOut) {
        this.stdOut = stdOut;
        return this;
    }

    Route getRoute() {
        return route;
    }
    OutputStream getDumpStream() { return dumpStream; }
    FeederParams setDumpStream(OutputStream dumpStream) {
        this.dumpStream = dumpStream;
        return this;
    }

    String getConfigId() {
        return configId;
    }

    FeederParams setConfigId(String configId) {
        this.configId = configId;
        return this;
    }

    boolean isSerialTransferEnabled() {
        return serialTransferEnabled;
    }

    FeederParams setSerialTransfer(boolean serial) {
        this.serialTransferEnabled = serial;
        return this;
    }

    int getNumDispatchThreads() { return numDispatchThreads; }

    FeederParams parseArgs(String... args) throws ParseException, FileNotFoundException {
        Options opts = new Options();
        opts.addOption("s", "serial", false, "use serial transfer mode, at most 1 pending operation");
        opts.addOption("n", "numthreads", true, "Number of clients for sending messages. Anything, but 1 will bypass sequencing by document id.");
        opts.addOption("r", "route", true, "Route for sending messages. default is 'default'....");
        opts.addOption("o", "output", true, "File to write to. Extensions gives format (.xml, .json, .v8) json will be produced if no extension.");

        CommandLine cmd = new DefaultParser().parse(opts, args);
        serialTransferEnabled = cmd.hasOption('s');
        if (cmd.hasOption('n')) {
            numDispatchThreads = Integer.valueOf(cmd.getOptionValue('n').trim());
        }
        if (cmd.hasOption('r')) {
            route = Route.parse(cmd.getOptionValue('r').trim());
        }
        if (cmd.hasOption('o')) {
            dumpStream = new FileOutputStream(new File(cmd.getOptionValue('o').trim()));
        }

        return this;
    }

}
