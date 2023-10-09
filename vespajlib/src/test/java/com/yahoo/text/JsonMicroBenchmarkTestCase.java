// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class JsonMicroBenchmarkTestCase {

    private static final long RUNTIME = 20L * 60L * 1000L;

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    enum Strategy {
        VESPAJLIB, JACKSON;
    }

    private static abstract class BenchFactory {
        abstract Bench produce();
    }

    private static class VespajlibFactory extends BenchFactory {

        @Override
        Bench produce() {
            return new OutputWithWriter();
        }

    }

    private static class JacksonFactory extends BenchFactory {
        @Override
        Bench produce() {
            return new OutputWithGenerator();
        }
    }

    private static abstract class Bench implements Runnable {
        public volatile long runs;
        public volatile long start;
        public volatile long end;
        public volatile long metric;

        /**
         * Object identity is used to differentiate between different implementation strategies, toString() is used to print a report.
         *
         * @return an object with a descriptive toString() for the implementation under test
         */
        abstract Object category();

        @Override
        public final void run() {
            Random random = new Random(42L);
            long localBytesWritten = 0L;
            long localRuns = 0;

            start = System.currentTimeMillis();
            long target = start + JsonMicroBenchmarkTestCase.RUNTIME;

            while (System.currentTimeMillis() < target) {
                for (int i = 0; i < 1000; ++i) {
                    localBytesWritten += iterate(random);
                }
                localRuns += 1000L;
            }
            end = System.currentTimeMillis();
            runs = localRuns;
            metric = localBytesWritten;
        }

        abstract int iterate(Random random);
    }

    private static final class OutputWithGenerator extends Bench {

        public OutputWithGenerator() {
        }


        int iterate(Random random) {
            JsonGenerator generator;
            ByteArrayOutputStream generatorOut = new ByteArrayOutputStream();
            try {
                generator = new JsonFactory().createJsonGenerator(generatorOut,
                        JsonEncoding.UTF8);
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
            try {
                serialize(generatedDoc(random), generator);
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
            try {
                generator.close();
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
            return generatorOut.toByteArray().length;
        }

        static void serialize(Map<String, Object> m, JsonGenerator g) throws IOException {
            g.writeStartObject();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                g.writeFieldName(e.getKey());
                serializeField(g, e.getValue());
            }
            g.writeEndObject();
        }

        @SuppressWarnings("unchecked")
        static void serializeField(JsonGenerator g, final Object value)
                throws IOException {
            if (value instanceof Map) {
                serialize((Map<String, Object>) value, g);
            } else if (value instanceof Number) {
                g.writeNumber(((Number) value).intValue());
            } else if (value instanceof String) {
                g.writeString((String) value);
            } else if (value instanceof List) {
                g.writeStartArray();
                for (Object o : (List<Object>) value) {
                    serializeField(g, o);
                }
                g.writeEndArray();
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        Object category() {
            return Strategy.JACKSON;
        }

    }

    private static final class OutputWithWriter extends Bench {

        OutputWithWriter() {
        }

        int iterate(Random random) {
            ByteArrayOutputStream writerOut = new ByteArrayOutputStream();
            JSONWriter writer = new JSONWriter(writerOut);
            try {
                serialize(generatedDoc(random), writer);
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
            return writerOut.toByteArray().length;
        }

        static void serialize(Map<String, Object> m, JSONWriter w) throws IOException {
            w.beginObject();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                w.beginField(e.getKey());
                final Object value = e.getValue();
                serializeField(w, value);
                w.endField();
            }
            w.endObject();
        }

        @SuppressWarnings("unchecked")
        static void serializeField(JSONWriter w, final Object value)
                throws IOException {
            if (value instanceof Map) {
                serialize((Map<String, Object>) value, w);
            } else if (value instanceof Number) {
                w.value((Number) value);
            } else if (value instanceof String) {
                w.value((String) value);
            } else if (value instanceof List) {
                w.beginArray();
                for (Object o : (List<Object>) value) {
                    w.beginArrayValue();
                    serializeField(w, o);
                    w.endArrayValue();
                }
                w.endArray();
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        Object category() {
            return Strategy.VESPAJLIB;
        }

    }

    @Test
    @Ignore
    public final void test() throws InterruptedException {
        final OutputWithWriter forWriter = new OutputWithWriter();
        Thread writerThread = new Thread(forWriter);
        final OutputWithGenerator forGenerator = new OutputWithGenerator();
        Thread generatorThread = new Thread(forGenerator);
        writerThread.start();
        generatorThread.start();
        writerThread.join();
        generatorThread.join();
        System.out.println("Generator time: " + (forGenerator.end - forGenerator.start));
        System.out.println("Writer time: " + (forWriter.end - forWriter.start));
        System.out.println("Output length from generator: " + forGenerator.metric);
        System.out.println("Output length from writer: " + forWriter.metric);
        System.out.println("Iterations with generator: " + forGenerator.runs);
        System.out.println("Iterations with writer: " + forWriter.runs);
        System.out.println("Iterations/s with generator: " + ((double) forGenerator.runs / (double) (forGenerator.end - forGenerator.start)) * 1000.0d);
        System.out.println("Iterations/s  with writer: " + ((double) forWriter.runs / (double) (forWriter.end - forWriter.start)) * 1000.0d);
    }

    @Test
    @Ignore
    public final void test16Threads() throws InterruptedException {
        List<Thread> threads = new ArrayList<>(16);
        List<Bench> benches = createBenches(8, new VespajlibFactory(), new JacksonFactory());

        for (Bench bench : benches) {
            threads.add(new Thread(bench));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("8 Jackson threads competing with 8 VespaJLib threads.");
        metrics(benches, Strategy.JACKSON);
        metrics(benches, Strategy.VESPAJLIB);
    }

    @Test
    @Ignore
    public final void test16ThreadsJacksonOnly() throws InterruptedException {
        List<Thread> threads = new ArrayList<>(16);
        List<Bench> benches = createBenches(16, new JacksonFactory());

        for (Bench bench : benches) {
            threads.add(new Thread(bench));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("16 Jackson threads.");
        metrics(benches, Strategy.JACKSON);
    }

    @Test
    @Ignore
    public final void test16ThreadsVespaJlibOnly() throws InterruptedException {
        List<Thread> threads = new ArrayList<>(16);
        List<Bench> benches = createBenches(16, new VespajlibFactory());

        for (Bench bench : benches) {
            threads.add(new Thread(bench));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("16 VespaJLib threads.");
        metrics(benches, Strategy.VESPAJLIB);
    }


    private void metrics(List<Bench> benches, Strategy choice) {
        List<Bench> chosen = new ArrayList<>();

        for (Bench b : benches) {
            if (b.category() == choice) {
                chosen.add(b);
            }
        }

        long[] rawTime = new long[chosen.size()];
        long[] rawOutputLength = new long[chosen.size()];
        long[] rawIterations = new long[chosen.size()];
        double[] rawIterationsPerSecond = new double[chosen.size()];

        for (int i = 0; i < chosen.size(); ++i) {
            Bench b = chosen.get(i);
            rawTime[i] = b.end - b.start;
            rawOutputLength[i] = b.metric;
            rawIterations[i] = b.runs;
            rawIterationsPerSecond[i] = ((double) b.runs) / (((double) (b.end - b.start)) / 1000.0d);
        }

        double avgTime = mean(rawTime);
        double avgOutputLength = mean(rawOutputLength);
        double avgIterations = mean(rawIterations);
        double avgIterationsPerSecond = mean(rawIterationsPerSecond);

        System.out.println("For " + choice + ":");
        dumpMetric("run time", rawTime, avgTime, "s", 0.001d);
        dumpMetric("output length", rawOutputLength, avgOutputLength, "bytes", 1.0d);
        dumpMetric("iterations", rawIterations, avgIterations, "", 1.0d);
        dumpMetric("iterations per second", rawIterationsPerSecond, avgIterationsPerSecond, "s**-1", 1.0d);
    }

    private void dumpMetric(String name, long[] raw, double mean, String unit, double scale) {
        System.out.println("Average " + name + ": " + mean  * scale + " " + unit);
        System.out.println("Mean absolute deviation of " + name + ": " + averageAbsoluteDeviationFromMean(raw, mean) * scale + " " + unit);
        System.out.println("Minimum " + name + ": " + min(raw) * scale + " " + unit);
        System.out.println("Maximum " + name + ": " + max(raw) * scale + " " + unit);
    }

    private void dumpMetric(String name, double[] raw, double mean, String unit, double scale) {
        System.out.println("Average " + name + ": " + mean  * scale + " " + unit);
        System.out.println("Mean absolute deviation of " + name + ": " + averageAbsoluteDeviationFromMean(raw, mean) * scale + " " + unit);
        System.out.println("Minimum " + name + ": " + min(raw) * scale + " " + unit);
        System.out.println("Maximum " + name + ": " + max(raw) * scale + " " + unit);
    }

    private List<Bench> createBenches(int ofEach, BenchFactory... factories) {
        List<Bench> l = new ArrayList<>(ofEach * factories.length);

        // note how the bench objects of different objects become intermingled, this is by design
        for (int i = 0; i < ofEach; ++i) {
            for (BenchFactory factory : factories) {
                l.add(factory.produce());
            }
        }
        return l;
    }

    private double mean(long[] values) {
        long sum = 0L;

        // ignore overflow :)
        for (long v : values) {
            sum += v;
        }
        return ((double) sum / (double) values.length);
    }

    private double mean(double[] values) {
        double sum = 0L;

        for (double v : values) {
            sum += v;
        }
        return sum / (double) values.length;
    }

    private double averageAbsoluteDeviationFromMean(long[] values, double mean) {
        double sum = 0.0d;

        for (long v : values) {
            sum += Math.abs(mean - (double) v);
        }

        return sum / (double) values.length;
    }

    private double averageAbsoluteDeviationFromMean(double[] values, double mean) {
        double sum = 0.0d;

        for (double v : values) {
            sum += Math.abs(mean - v);
        }

        return sum / (double) values.length;
    }

    private long min(long[] values) {
        long min = Long.MAX_VALUE;

        for (long v : values) {
            min = Math.min(min, v);
        }
        return min;
    }

    private double min(double[] values) {
        double min = Double.MAX_VALUE;

        for (double v : values) {
            min = Math.min(min, v);
        }
        return min;
    }

    private long max(long[] values) {
        long max = Long.MIN_VALUE;

        for (long v : values) {
            max = Math.max(max, v);
        }
        return max;
    }

    private double max(double[] values) {
        double max = Double.MIN_VALUE;

        for (double v : values) {
            max = Math.max(max, v);
        }
        return max;
    }

    @SuppressWarnings("null")
    @Test
    @Ignore
    public final void testSanity() throws IOException {
        @SuppressWarnings("unused")
        String a, b;
        {
            Random random = new Random(42L);
            JsonGenerator generator = null;
            ByteArrayOutputStream generatorOut = new ByteArrayOutputStream();
            try {
                generator = new JsonFactory().createJsonGenerator(generatorOut,
                        JsonEncoding.UTF8);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
            try {
                OutputWithGenerator.serialize(generatedDoc(random), generator);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
            try {
                generator.close();
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
            a = generatorOut.toString("UTF-8");
        }
        {
            Random random = new Random(42L);
            ByteArrayOutputStream writerOut = new ByteArrayOutputStream();
            JSONWriter writer = new JSONWriter(writerOut);
            try {
                OutputWithWriter.serialize(generatedDoc(random), writer);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
            b = writerOut.toString("UTF-8");
        }
        // dumpToFile("/tmp/a", a);
        // dumpToFile("/tmp/b", b);
    }

    @SuppressWarnings("unused")
    private void dumpToFile(String path, String b) throws IOException {
        FileWriter f = new FileWriter(path);
        f.write(b);
        f.close();
    }

    static Map<String, Object> generatedDoc(Random random) {
        return generateObject(random, 0, random.nextInt(8));
    }

    static String generateFieldName(Random random) {
        int len = random.nextInt(100) + 3;
        char[] base = new char[len];
        for (int i = 0; i < len; ++i) {
            base[i] = (char) (random.nextInt(26) + 'a');
        }
        return new String(base);
    }

    static byte[] generateByteArrayPayload(Random random) {
        return null;
    }

    static String generateStringPayload(Random random) {
        int len = random.nextInt(100) + random.nextInt(100) + random.nextInt(100) + random.nextInt(100);
        char[] base = new char[len];
        for (int i = 0; i < len; ++i) {
            base[i] = (char) random.nextInt(0xd800);
        }
        return new String(base);
    }

    static Number generateInt(Random random) {
        return Integer.valueOf(random.nextInt());
    }

    static List<Object> generateArray(Random random, int nesting, int maxNesting) {
        int len = random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
        List<Object> list = new ArrayList<>(len);
        for (int i = 0; i < len; ++i) {
            list.add(generateStuff(random, nesting, maxNesting));
        }
        return list;
    }

    private static Object generateStuff(Random random, int nesting, int maxNesting) {
        if (nesting >= maxNesting) {
            return generatePrimitive(random);
        } else {
            final int die = random.nextInt(10);
            if (die == 9) {
                return generateObject(random, nesting + 1, maxNesting);
            } else if (die == 8) {
                return generateArray(random, nesting + 1, maxNesting);
            } else {
                return generatePrimitive(random);
            }
        }
    }

    private static Object generatePrimitive(Random random) {
        if (random.nextInt(2) == 0) {
            return generateStringPayload(random);
        } else {
            return generateInt(random);
        }
    }

    static Map<String, Object> generateObject(Random random, int nesting, int maxNesting) {
        int len = random.nextInt(5) + random.nextInt(5) + random.nextInt(5) + random.nextInt(5);
        Map<String, Object> m = new TreeMap<>();
        for (int i = 0; i < len; ++i) {
            m.put(generateFieldName(random), generateStuff(random, nesting, maxNesting));
        }
        return m;
    }

}
