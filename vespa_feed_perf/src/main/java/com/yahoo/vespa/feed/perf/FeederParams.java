// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.messagebus.routing.Route;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * @author Simon Thoresen Hult
 */
public class FeederParams {

    private InputStream stdIn = System.in;
    private PrintStream stdErr = System.err;
    private PrintStream stdOut = System.out;
    private Route route = Route.parse("default");
    private String configId = "client";
    private boolean serialTransferEnabled = false;

    public InputStream getStdIn() {
        return stdIn;
    }

    public FeederParams setStdIn(InputStream stdIn) {
        this.stdIn = stdIn;
        return this;
    }

    public PrintStream getStdErr() {
        return stdErr;
    }

    public FeederParams setStdErr(PrintStream stdErr) {
        this.stdErr = stdErr;
        return this;
    }

    public PrintStream getStdOut() {
        return stdOut;
    }

    public FeederParams setStdOut(PrintStream stdOut) {
        this.stdOut = stdOut;
        return this;
    }

    public Route getRoute() {
        return route;
    }

    public FeederParams setRoute(Route route) {
        this.route = new Route(route);
        return this;
    }

    public String getConfigId() {
        return configId;
    }

    public FeederParams setConfigId(String configId) {
        this.configId = configId;
        return this;
    }

    public boolean isSerialTransferEnabled() {
        return serialTransferEnabled;
    }

    public FeederParams setSerialTransfer(boolean serial) {
        this.serialTransferEnabled = serial;
        return this;
    }

    public FeederParams parseArgs(String... args) throws ParseException {
        Options opts = new Options();
        opts.addOption("s", "serial", false, "use serial transfer mode, at most 1 pending operation");

        CommandLine cmd = new DefaultParser().parse(opts, args);
        serialTransferEnabled = cmd.hasOption("s");
        route = newRoute(cmd.getArgs());
        return this;
    }

    private static Route newRoute(String... args) {
        if (args.length == 0) {
            return Route.parse("default");
        }
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            out.append(arg).append(' ');
        }
        return Route.parse(out.toString());
    }
}
