// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * @author baldersheim
 * @since 5.2
 */

public class Utf8MicroBencmark {
    public void benchmark(int sizeInK) {
        String [] l = new String[1000];
        for (int i=0; i < l.length; i++) {
            l[i] = "typical ascii string" + i;
        }
        System.out.println("Warming up...");
        utf8encode(l, 10000); // warm-up
        utf8encodeFast(l, 10000);

        long startTime, endTime, sum;
        System.out.println("Starting benchmark ...");
        startTime=System.currentTimeMillis();
        sum = utf8encode(l, sizeInK);
        endTime=System.currentTimeMillis();
        System.out.println("Utf8 encoding " + sizeInK + "k strings took " + (endTime-startTime) + "ms generating " + sum + "bytes");
        startTime=System.currentTimeMillis();
        sum = utf8encodeFast(l, sizeInK);
        endTime=System.currentTimeMillis();
        System.out.println("Utf8 fast encoding " + sizeInK + "k strings took " + (endTime-startTime) + "ms generating " + sum + "bytes");
    }

    private long utf8encode(String [] l, int sizeInK) {
        long sum = 0;
        for (int i=0; i<1000*sizeInK; i++) {
            sum += Utf8.toBytesStd(l[i%l.length]).length;
        }
        return sum;
    }
    private long utf8encodeFast(String [] l, int sizeInK) {
        long sum = 0;
        for (int i=0; i<1000*sizeInK; i++) {
            sum += Utf8.toBytes(l[i%l.length]).length;
        }
        return sum;
    }

    public static void main(String[] args) {
        new Utf8MicroBencmark().benchmark(10000);
    }

}
