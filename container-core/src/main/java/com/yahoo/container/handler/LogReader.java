package com.yahoo.container.handler;

import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class LogReader {

    protected static JSONObject readLogs(String logDirectory) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        File root = new File(logDirectory);
        traverse_folder(root, json);
        return json;
    }

    private static void traverse_folder(File root, JSONObject json) throws IOException, JSONException {
        for(File child : root.listFiles()) {
            JSONObject childJson = new JSONObject();
            if(child.isFile()) {
                json.put(child.getName(), DatatypeConverter.printBase64Binary(Files.readAllBytes(child.toPath())));
            }
            else {
                json.put(child.getName(), childJson);
                traverse_folder(child, childJson);
            }
        }
    }
}
