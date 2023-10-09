// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * It is what it says
 *
 * @author bratseth
 */
public class XMLMicroBenchmark {

    public void benchmark(int sizeInK) {
        System.out.println("Warming up...");
        escapeStrings(1000); // warm-up

        System.out.println("Starting benchmark...");
        long startTime=System.currentTimeMillis();
        escapeStrings(sizeInK);
        long endTime=System.currentTimeMillis();
        System.out.println("Done.\nEscaping " + sizeInK + "k strings took " + (endTime-startTime) + "ms");
    }

    private void escapeStrings(int sizeInK) {
        for (int i=0; i<1000*sizeInK; i++) {
            XML.xmlEscape("foobar" + i,true,true,'\u001f');
        }
    }

    public static void main(String[] args) {
        new XMLMicroBenchmark().benchmark(10000);
    }

}
