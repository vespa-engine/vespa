package com.yahoo.vespa.hosted.node.verification.hardware.mock;

import com.yahoo.vespa.hosted.node.verification.hardware.CommandExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by olaa on 17/07/2017.
 */
public class MockCommandExecutor extends CommandExecutor {

    private ArrayList<String> mockCommands;
    private int counter;
    public static final String DUMMY_COMMAND = "DUMMY";

    public MockCommandExecutor() {
        mockCommands = new ArrayList<>();
        counter = 0;
    }

    @Override
    public ArrayList<String> executeCommand(String command) throws IOException{
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

    public ArrayList<String> readFromFile(String filepath) throws IOException {
        return new ArrayList<>(Arrays.asList(new String(Files.readAllBytes(Paths.get(filepath))).split("\n")));
    }

    public ArrayList<String> outputFromString(String output) {
        return new ArrayList<>(Arrays.asList(output.split("\n")));
    }
}
