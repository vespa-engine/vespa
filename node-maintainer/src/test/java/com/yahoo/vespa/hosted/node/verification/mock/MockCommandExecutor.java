// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.mock;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author sgrostad
 * @author olaaun
 */

public class MockCommandExecutor extends CommandExecutor {

    private ArrayList<String> mockCommands;
    private int counter;
    private static final String DUMMY_COMMAND = "DUMMY";

    public MockCommandExecutor() {
        mockCommands = new ArrayList<>();
        counter = 0;
    }

    @Override
    public List<String> executeCommand(String command) throws IOException {
        String mockCommand = mockCommands.get(counter++);
        if (mockCommand.equals(DUMMY_COMMAND)) return null;
        return super.executeCommand(mockCommand);
    }

    public void addCommand(String command) {
        mockCommands.add(command);
    }

    public void addDummyCommand() {
        mockCommands.add(DUMMY_COMMAND);
    }

    public static List<String> readFromFile(String filepath) throws IOException {
        return new ArrayList<>(Arrays.asList(new String(Files.readAllBytes(Paths.get(filepath))).split("\n")));
    }

    public List<String> outputFromString(String output) {
        return new ArrayList<>(Arrays.asList(output.split("\n")));
    }

}
