// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author baldersheim
 */
public class CollectionsBenchMark {
    abstract static class BenchMark {
        protected BenchMark(int numWarmup, int repetitions) {
            this.numWarmup = numWarmup;
            this.repetitions = repetitions;
        }
        abstract void runOnce();
        abstract String getEndComment();
        protected void run() {
            System.out.println("Starting benchmark warmup '" + getClass().getName() + "'.");
            for (int i=0; i < numWarmup; i++) {
                runOnce();
            }
            System.out.println("Starting benchmark '" + getClass().getName() + "'.");
            long startTime=System.currentTimeMillis();
            for (int i=0; i < repetitions; i++) {
                runOnce();
            }
            long endTime=System.currentTimeMillis();
            long totalTime=(endTime-startTime);
            System.out.println("Done in " + totalTime + " ms (" + ((float) totalTime * 1000 / repetitions + " microsecond per repetition.)")); // *2 because we do 2 gets
            System.out.println("Final remark: " + getEndComment());
        }


        final private int repetitions;
        final private int numWarmup;
    }

    static class MapFilterBenchMark extends BenchMark {
        MapFilterBenchMark(Map<Integer, Integer> s, int numWarmup, int numRepetitions, int numObjects) {
            super(numWarmup, numRepetitions);
            this.s = s;
            objects = new Integer[numObjects];
            for (int i=0; i < numObjects; i++) {
                objects[i] = i;
            }
        }
        void runOnce() {
            for (Integer o : objects) {
                if (s.put(o, o) == null) {
                    uniqueCount += o;
                }
            }
        }
        String getEndComment() { return " Unique sum is '" + uniqueCount + "'"; }
        private final Map<Integer,Integer> s;
        final Integer [] objects;
        long uniqueCount = 0;
    }

    static class SetFilterBenchMark extends BenchMark {
        SetFilterBenchMark(Set<Integer> s, int numWarmup, int numRepetitions, int numObjects) {
            super(numWarmup, numRepetitions);
            this.s = s;
            objects = new Integer[numObjects];
            for (int i=0; i < numObjects; i++) {
                objects[i] = i;
            }
        }
        void runOnce() {
            for (Integer o : objects) {
                if ( s.add(o) ) {
                    uniqueCount += o;
                }
            }
        }
        String getEndComment() { return " Unique sum is '" + uniqueCount + "'"; }
        private final Set<Integer> s;
        final Integer [] objects;
        long uniqueCount = 0;
    }

    static abstract class SmallMapsBenchMark extends BenchMark {
        SmallMapsBenchMark(int numWarmup, int numRepetitions, int numObjects, int numUnique) {
            super(numWarmup, numRepetitions);
            objects = new Integer[numObjects];
            for (int i=0; i < numObjects; i++) {
                objects[i] = i%numUnique;
            }
        }
        void runOnce() {
            Set<Integer> s = createSet();
            for (Integer o : objects) {
                if ( s.add(o) ) {
                    uniqueCount += o;
                }
            }
        }
        abstract Set<Integer> createSet();
        String getEndComment() { return " Unique sum is '" + uniqueCount + "'"; }
        final Integer [] objects;
        long uniqueCount = 0;
    }

    static class SmallHashSetBenchMark extends SmallMapsBenchMark
    {
        SmallHashSetBenchMark(int numWarmup, int numRepetitions, int numObjects, int numUnique) {
            super(numWarmup, numRepetitions, numObjects, numUnique);
        }
        Set<Integer> createSet() { return new HashSet<Integer>();}
    }

    static class SmallLazySetBenchMark extends SmallMapsBenchMark
    {
        SmallLazySetBenchMark(int numWarmup, int numRepetitions, int numObjects, int numUnique) {
            super(numWarmup, numRepetitions, numObjects, numUnique);
        }
        Set<Integer> createSet() { return new LazySet<Integer>() {
                @Override
                protected Set<Integer> newDelegate() {
                    return new HashSet<Integer>();
                }
            };
        }
    }

    static class SmallLazyTinyBenchMark extends SmallMapsBenchMark
    {
        SmallLazyTinyBenchMark(int numWarmup, int numRepetitions, int numObjects, int numUnique) {
            super(numWarmup, numRepetitions, numObjects, numUnique);
        }
        Set<Integer> createSet() { return new LazySet<Integer>() {
            @Override
            protected Set<Integer> newDelegate() {
                return new TinyIdentitySet<Integer>(10);
            }
        };
        }
    }

    static void benchMarkAll() {

        new MapFilterBenchMark(new HashMap<Integer, Integer>(), 100000, 10000000, 10).run();
        new MapFilterBenchMark(new IdentityHashMap<Integer, Integer>(10), 100000, 10000000, 10).run();
        new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 10).run();
        new SetFilterBenchMark(new TinyIdentitySet<Integer>(10), 100000, 10000000, 10).run();
        new SetFilterBenchMark(new TinyIdentitySet<Integer>(10), 100000, 10000000, 15).run();
        new SetFilterBenchMark(new TinyIdentitySet<Integer>(10), 100000, 10000000, 20).run();
        new SetFilterBenchMark(new TinyIdentitySet<Integer>(20), 100000, 10000000, 20).run();
        new SmallHashSetBenchMark(100000, 10000000, 10, 1).run();
        new SmallLazySetBenchMark(100000, 10000000, 10, 1).run();
        new SmallHashSetBenchMark(100000, 10000000, 10, 2).run();
        new SmallLazySetBenchMark(100000, 10000000, 10, 2).run();
        new SmallLazyTinyBenchMark(100000, 10000000, 10, 2).run();
        new SmallHashSetBenchMark(100000, 10000000, 10, 10).run();
        new SmallLazySetBenchMark(100000, 10000000, 10, 10).run();

        new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 12).run();

        new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 20).run();
        new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 25).run();
        new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 30).run();
        new SmallHashSetBenchMark(100000, 10000000, 1, 1).run();

    }

    static void benchMark() {

        //new MapFilterBenchMark(new HashMap<Integer, Integer>(), 100000, 10000000, 10).run();
        //new MapFilterBenchMark(new IdentityHashMap<Integer, Integer>(10), 100000, 10000000, 10).run();
        //new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 10).run();
        //new SetFilterBenchMark(new TinyIdentitySet<Integer>(10), 100000, 10000000, 10).run();
        //new SmallHashSetBenchMark(100000, 10000000, 10, 1).run();
        //new SmallLazySetBenchMark(100000, 10000000, 10, 1).run();
        //new SmallHashSetBenchMark(100000, 10000000, 10, 2).run();
        //new SmallLazySetBenchMark(100000, 10000000, 10, 2).run();
        new SmallLazyTinyBenchMark(100000, 10000000, 10, 2).run();
        //new SmallHashSetBenchMark(100000, 10000000, 10, 10).run();
        //new SmallLazySetBenchMark(100000, 10000000, 10, 10).run();

        //new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 12).run();

        //new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 20).run();
        //new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 25).run();
        //new SetFilterBenchMark(new HashSet<Integer>(), 100000, 10000000, 30).run();
        //new SmallHashSetBenchMark(100000, 10000000, 1, 1).run();

    }


    static public void main(String argv[]) {
        benchMarkAll();
        ExecutorService tp = Executors.newFixedThreadPool(16);

        for (int i=0; i < 16; i++) {
            tp.execute(new Runnable() {
                @Override
                public void run() {
                    benchMark();
                }
            });
        }
    }
}
