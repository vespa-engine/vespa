package com.yahoo.vespa.hosted.node.verification.commons;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * Created by olaa on 03/07/2017.
 */
public class CommandExecutor {

    public ArrayList<String> executeCommand(String command) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArrayList<String> results = new ArrayList<>();
        writeToOutputStream(outputStream, command);
        writeOutputStreamToResults(outputStream, results);
        return results;
    }

    private void writeToOutputStream(ByteArrayOutputStream outputStream, String command) throws IOException {
        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        executor.execute(cmdLine);
    }


    private void writeOutputStreamToResults(ByteArrayOutputStream outputStream, ArrayList<String> results) throws IOException {
        String out = outputStream.toString();
        BufferedReader br = new BufferedReader(new StringReader(out));
        String line;
        while ((line = br.readLine()) != null) {
            results.add(line);
        }
    }
}