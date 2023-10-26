// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

/**
 * Compares alternative ways of appending strings
 *
 * @author bratseth
 */
public class StringAppendMicroBenchmarkTest {

    private static abstract class Benchmark {

        private int repetitions=10000000;

        public void execute() {
            System.out.println("Executing benchmark '" + getName() + "' ...");
            append(100000); // warm-up
            long start=System.currentTimeMillis();
            append(repetitions);
            long duration=System.currentTimeMillis()-start;
            System.out.println("Completed " + repetitions + " repetitions in " + duration + " ms\n");
        }

        private int append(int repetitions) {
            String prefix="hello";
            int totalSize=0;
            for (int i=0; i<repetitions; i++) {
                String full=appendStrings(prefix, String.valueOf(i));
                totalSize+=full.length();
            }
            return totalSize;
        }

        protected abstract String getName();
        protected abstract String appendStrings(String a,String b);

    }

    private static final class PlusOperatorBenchmark extends Benchmark {

        @Override
        protected String getName() { return "Plus operator"; }

        @Override
        protected String appendStrings(String a, String b) {
            return a+b;
        }

    }

    private static final class StringConcatBenchmark extends Benchmark {

        @Override
        protected String getName() { return "String concat"; }

        @Override
        protected String appendStrings(String a, String b) {
            return a.concat(b);
        }

    }

    /**
     * Make Clover shut up about this in the coverage report.
     */
    @Test
    public void shutUpClover() {
    }

    public static void main(String[] args) {
        new PlusOperatorBenchmark().execute(); // Typical number on my box with Java 7: 1000 ms
        new StringConcatBenchmark().execute(); // Typical number on my box with Java 7: 1150 ms
    }

}
