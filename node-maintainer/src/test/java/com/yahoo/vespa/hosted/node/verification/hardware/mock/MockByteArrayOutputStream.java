package com.yahoo.vespa.hosted.node.verification.hardware.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by olaa on 13/07/2017.
 */
public class MockByteArrayOutputStream extends ByteArrayOutputStream {

    String outputString;

    public ArrayList<String> readFromFile(String filepath) throws IOException {
        return new ArrayList<>(Arrays.asList(new String(Files.readAllBytes(Paths.get(filepath))).split("\n")));

        //outputString = new String(Files.readAllBytes(Paths.get(filepath)));
    }

    public void setExpectedToString(String outputString){
        this.outputString = outputString;
    }

    @Override
    public String toString(){
        return this.outputString;
    }
}
