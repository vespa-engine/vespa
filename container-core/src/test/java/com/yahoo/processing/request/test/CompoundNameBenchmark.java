// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.test;

import com.yahoo.processing.request.CompoundName;

/**
 * @author baldersheim
 */
public class CompoundNameBenchmark {
    public void run() {
        long result=0;
        String strings[] = createStrings(1000);
        // Warm-up
        out("Warming up...");
        for (int i=0; i<10*1000; i++)
            result+=createCompundName(strings);

        long startTime=System.currentTimeMillis();
        out("Running...");
        for (int i=0; i<100*1000; i++)
            result+=createCompundName(strings);
        out("Ignore this: " + result); // Make sure we are not fooled by optimization by creating an observable result
        long endTime=System.currentTimeMillis();
        out("Compoundification 1000 strings 100.000 times took " + (endTime-startTime) + " ms");
    }

    private final String [] createStrings(int num) {
        String strings [] = new String [num];
        for(int i=0; i < strings.length; i++) {
            strings[i] = "this.is.a.short.compound.name." + i;
        }
        return strings;
    }

    private final int createCompundName(String [] strings) {
        int retval = 0;
        for (int i=0; i < strings.length; i++) {
            CompoundName n = new CompoundName(strings[i]);
            retval += n.size();
        }
        return retval;
    }

    private void out(String string) {
        System.out.println(string);
    }

    public static void main(String[] args) {
        new CompoundNameBenchmark().run();
    }

}
