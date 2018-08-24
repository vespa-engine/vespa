package com.yahoo.container.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;

public class LogReader {

    protected static void writeToOutputStream(String logDirectory, OutputStream outputStream) throws IOException {
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
        JSONObject json = new JSONObject();
        File root = new File(logDirectory);
        try {
            traverse_folder(root, json);
        } catch (JSONException e) {
            outputStreamWriter.write("Failed to create log JSON");
        }
        outputStreamWriter.write(json.toString());
        outputStreamWriter.close();
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
