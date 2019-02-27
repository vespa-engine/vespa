// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.util.Base64;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class LogReader {

    long earliestLogThreshold;
    long latestLogThreshold;

    protected JSONObject readLogs(String logDirectory, long earliestLogThreshold, long latestLogThreshold) throws IOException, JSONException {
        this.earliestLogThreshold = earliestLogThreshold;
        this.latestLogThreshold = latestLogThreshold + Duration.ofMinutes(5).toMillis(); // Add some time to allow retrieving logs currently being modified
        JSONObject json = new JSONObject();
        File root = new File(logDirectory);
        traverse_folder(root, json, "");
        return json;
    }

    private void traverse_folder(File root, JSONObject json, String filename) throws IOException, JSONException {
        File[] files = root.listFiles();
        for(File child : files) {
            long logTime = Files.getLastModifiedTime(child.toPath()).toMillis();
            if(child.isFile() && earliestLogThreshold < logTime && logTime < latestLogThreshold) {
                json.put(filename + child.getName(), Base64.getEncoder().encodeToString(Files.readAllBytes(child.toPath())));
            }
            else if (!child.isFile()){
                traverse_folder(child, json, filename + child.getName() + "-");
            }
        }
    }

}
