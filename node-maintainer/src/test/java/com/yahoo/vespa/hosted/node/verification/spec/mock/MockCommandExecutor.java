package com.yahoo.vespa.hosted.node.verification.spec.mock;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.CommandExecutor;

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

    public MockCommandExecutor() {
        mockCommands = new ArrayList<>();
        counter = 0;
    }

    @Override
    public ArrayList<String> executeCommand(String command) throws IOException{
        return super.executeCommand(mockCommands.get(counter++));
    }

    public void addCommand(String command) {
        mockCommands.add(command);
    }

    public static ArrayList<String> readFromFile(String filepath) throws IOException {
        return new ArrayList<>(Arrays.asList(new String(Files.readAllBytes(Paths.get(filepath))).split("\n")));
    }

    public ArrayList<String> outputFromString(String output) {
        return new ArrayList<>(Arrays.asList(output.split("\n")));
    }
}
