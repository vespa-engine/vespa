// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

public class LogReader {

    long earliestLogThreshold;
    long latestLogThreshold;

    protected JSONObject readLogs(String logDirectory, long earliestLogThreshold, long latestLogThreshold) throws IOException, JSONException {
        this.earliestLogThreshold = earliestLogThreshold;
        this.latestLogThreshold = latestLogThreshold;
        JSONObject json = new JSONObject();
        File root = new File(logDirectory);
        traverse_folder(root, json, "");
        return json;
    }

    private void traverse_folder(File root, JSONObject json, String filename) throws IOException, JSONException {
        File[] files = root.listFiles();
        for(File child : files) {
            long logTime = Files.readAttributes(child.toPath(), BasicFileAttributes.class).creationTime().toMillis();
            if(child.isFile() && earliestLogThreshold < logTime && logTime < latestLogThreshold) {
                json.put(filename + child.getName(), DatatypeConverter.printBase64Binary(Files.readAllBytes(child.toPath())));
            }
            else if (!child.isFile()){
                traverse_folder(child, json, filename + child.getName() + "-");
            }
        }
    }

}
