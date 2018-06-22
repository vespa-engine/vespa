// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
/**
 * @author arnej27959
 */
public class DoubleToStringBenchmark {

    @Test
    @Ignore
    public void benchmarkStringConstruction() throws Exception {
        List<Class<? extends Benchmark.Task>> taskList = Arrays.asList(UseStringValueOf.class,
                                                                       UseDoubleFormatter.class,
                                                                       UseDoubleFormatter.class,
                                                                       UseStringValueOf.class,
                                                                       UseStringValueOf.class,
                                                                       UseDoubleFormatter.class,
                                                                       UseDoubleFormatter.class,
                                                                       UseStringValueOf.class,
                                                                       UseDoubleFormatter.class,
                                                                       UseStringValueOf.class);

        int maxThreads = 256;
        int dummy = 0;
        System.out.print("warmup");
        for (Class<? extends Benchmark.Task> taskClass : taskList) {
            dummy += runBenchmark(maxThreads, taskClass);
            System.out.print(".");
        }
        System.out.println(" " + dummy);

        System.out.format("%-35s", "");
        for (int numThreads = 1; numThreads <= maxThreads; numThreads *= 2) {
            System.out.format("%13s t ", numThreads);
        }
        System.out.println();
        for (Class<? extends Benchmark.Task> taskClass : taskList) {
            System.out.format("%-35s", taskClass.getSimpleName());
            for (int numThreads = 1; numThreads <= maxThreads; numThreads *= 2) {
                System.out.format("%15d ", runBenchmark(numThreads, taskClass));
            }
            System.out.println();
        }
    }

    private long runBenchmark(int numThreads, Class<? extends Benchmark.Task> taskClass) throws Exception {
        return new Benchmark.Builder()
                .setNumIterationsPerThread(80000)
                .setNumThreads(numThreads)
                .setTaskClass(taskClass)
                .build()
                .run();
    }

    public static class UseStringValueOf implements Benchmark.Task {

        private long timeIt(Random randomGen, int num) {
            long before = System.nanoTime();

            String str = null;
            double v = 0.0;
            for (int i = 0; i < num; ++i) {
                v = randomGen.nextDouble() * 1.0e-2;
                str = String.valueOf(v);
            }

            long after = System.nanoTime();
            assertEquals(""+v, str);
            return after - before;
        }

        @Override
        public long run(CyclicBarrier barrier, int numIterations) throws Exception {
            Random randomGen = new Random(0xDeadBeef);
            barrier.await(600, TimeUnit.SECONDS);
            long t1 = timeIt(randomGen, numIterations / 4);
            long t2 = timeIt(randomGen, numIterations / 2);
            long t3 = timeIt(randomGen, numIterations / 4);
	    return t2;
        }
    }

    public static class UseDoubleFormatter implements Benchmark.Task {

        private long timeIt(Random randomGen, int num) {
            long before = System.nanoTime();

            String str = null;
            double v = 0.0;
            for (int i = 0; i < num; ++i) {
                v = randomGen.nextDouble() * 1.0e-2;
                str = DoubleFormatter.stringValue(v);
            }

            long after = System.nanoTime();
            // assertEquals(""+v, str);
            return after - before;
        }


        @Override
        public long run(CyclicBarrier barrier, int numIterations) throws Exception {
            Random randomGen = new Random(0xDeadBeef);
            barrier.await(600, TimeUnit.SECONDS);
            long t1 = timeIt(randomGen, numIterations / 4);
            long t2 = timeIt(randomGen, numIterations / 2);
            long t3 = timeIt(randomGen, numIterations / 4);
	    return t2;
        }
    }

}
