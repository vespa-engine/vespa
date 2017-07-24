package com.yahoo.vespa.hosted.node.verification.hardware;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * Created by sgrostad on 11/07/2017.
 */
public class CommandExecutor {

    public ArrayList<String> executeCommand(String command) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writeToOutputStream(outputStream, command);
        return readOutputStream(outputStream);
    }

    private void writeToOutputStream(ByteArrayOutputStream outputStream, String command) throws IOException {
        CommandLine cmdLine = new CommandLine("/bin/bash");
        String[] c = { "-c", command};
        cmdLine.addArguments(c, false);
        DefaultExecutor executor = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        executor.execute(cmdLine);
    }


    private ArrayList<String> readOutputStream(ByteArrayOutputStream outputStream) throws IOException {
        ArrayList<String> results = new ArrayList<>();
        String out = outputStream.toString();
        BufferedReader br = new BufferedReader(new StringReader(out));
        String line;
        while((line = br.readLine()) != null) {
            results.add(line);
        }
        return results;
    }
}
