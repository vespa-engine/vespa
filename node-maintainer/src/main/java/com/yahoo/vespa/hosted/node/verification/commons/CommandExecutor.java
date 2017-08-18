// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for executing terminal commands
 *
 * @author olaaun
 * @author sgrostad
 */
public class CommandExecutor {

    public List<String> executeCommand(String command) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<String> results = new ArrayList<>();
        writeToOutputStream(outputStream, command);
        writeOutputStreamToResults(outputStream, results);
        return results;
    }

    private void writeToOutputStream(ByteArrayOutputStream outputStream, String command) throws IOException {
        CommandLine cmdLine = new CommandLine("/bin/bash");
        cmdLine.addArgument("-c", false);
        cmdLine.addArgument(command, false);
        DefaultExecutor executor = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        executor.execute(cmdLine);
    }

    private void writeOutputStreamToResults(ByteArrayOutputStream outputStream, List<String> results) throws IOException {
        String out = outputStream.toString();
        BufferedReader br = new BufferedReader(new StringReader(out));
        String line;
        while ((line = br.readLine()) != null) {
            results.add(line);
        }
    }

}
