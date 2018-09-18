package com.yahoo.container.handler;

import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class LogReader {

    long earliestLogThreshold;
    long latestLogThreshold;

    public LogReader(long earliestLogThreshold, long latestLogThreshold) {
        this.earliestLogThreshold = earliestLogThreshold;
        this.latestLogThreshold = latestLogThreshold;
    }

    protected JSONObject readLogs(String logDirectory) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        File root = new File(logDirectory);
        traverse_folder(root, json, "");
        return json;
    }

    private void traverse_folder(File root, JSONObject json, String filename) throws IOException, JSONException {
        File[] files = root.listFiles();
        for(File child : files) {
            File temp = child;
            JSONObject childJson = new JSONObject();
            long logTime = child.lastModified();
            if(child.isFile() && earliestLogThreshold < logTime && logTime < latestLogThreshold) {
                json.put(filename + child.getName(), DatatypeConverter.printBase64Binary(Files.readAllBytes(child.toPath())));
            }
            else if (!child.isFile()){
                traverse_folder(child, json, filename + child.getName() + "-");
            }
        }
    }

}
