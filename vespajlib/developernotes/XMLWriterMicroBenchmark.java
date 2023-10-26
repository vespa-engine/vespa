// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import com.yahoo.io.ByteWriter;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * It is what it says
 *
 * @author bratseth
 */
public class XMLWriterMicroBenchmark {

    private final ByteArrayOutputStream output;
    private final XMLWriter xmlWriter;

    public XMLWriterMicroBenchmark(boolean optimize) {
        // setup
        output=new ByteArrayOutputStream();
        Charset cs = Charset.forName("utf-8");
        CharsetEncoder encoder = cs.newEncoder();
        xmlWriter=new XMLWriter(new ByteWriter(output, encoder), optimize);
    }

    public void benchmark(int sizeInK,boolean verifyOutput) {
        System.out.println("Warming up...");
        writeStrings(1000); // warm-up

        System.out.println("Starting benchmark...");
        long startTime=System.currentTimeMillis();
        writeStrings(sizeInK);
        long endTime=System.currentTimeMillis();
        System.out.println("Done.\nWriting " + sizeInK + "k strings took " + (endTime-startTime) + "ms");

        if (verifyOutput) {
            System.out.println("First 1k of output:");
            String result=null;
            try { result=output.toString("utf-8"); } catch (UnsupportedEncodingException e) { throw new RuntimeException(e); }
            System.out.println(result.substring(0,Math.min(500,result.length())));
        }
    }

    private void writeStrings(int sizeInK) {
        for (int i=0; i<1000*sizeInK; i++) {
            xmlWriter.openTag("dummytag").content(i,false).closeTag();
        }
    }

    public static void main(String[] args) {
        System.out.println("Unoptimized: -------------------------");
        new XMLWriterMicroBenchmark(false).benchmark(10000,false);
        System.out.println("Optimized:   ------------------------");
        new XMLWriterMicroBenchmark(true).benchmark(10000,false);
    }


}
