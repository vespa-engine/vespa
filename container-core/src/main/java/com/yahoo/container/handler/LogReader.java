package com.yahoo.container.handler;

import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

public class LogReader {

    int numberOfLogs;

    public LogReader() {
        this.numberOfLogs = -1;
    }

    public LogReader(int numberOfLogs) {
        this.numberOfLogs = numberOfLogs;
    }

    protected JSONObject readLogs(String logDirectory) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        File root = new File(logDirectory);
        traverse_folder(root, json);
        return json;
    }

    private void traverse_folder(File root, JSONObject json) throws IOException, JSONException {
        File[] files = root.listFiles();
        Arrays.sort(files,new Comparator<File>(){
            public int compare(File f1, File f2)
            {
                return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
            } });
        Arrays.sort(files, Comparator.reverseOrder());
        for(File child : files) {
            if (numberOfLogs == 0) return;
            JSONObject childJson = new JSONObject();
            if(child.isFile()) {
                json.put(child.getName(), DatatypeConverter.printBase64Binary(Files.readAllBytes(child.toPath())));
                decrementLogNumber();
            }
            else {
                json.put(child.getName(), childJson);
                traverse_folder(child, childJson);
            }
        }
    }

    private void decrementLogNumber() {
        numberOfLogs -= 1;
    }

}
