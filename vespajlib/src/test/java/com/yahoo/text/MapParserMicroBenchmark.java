// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.HashMap;
import java.util.Map;

/**
 * A benchmark of map parsing.
 * Expected time on Jon's mac: 200 microseconds per 1k size map.
 *
 * @author bratseth
 */
public class MapParserMicroBenchmark {

    private static String generateValues(int size) {
        StringBuilder b = new StringBuilder("{");
        for (int i=0; i<size; i++)
            b.append("a").append(i).append(":").append(i+1).append(",");
        b.setLength(b.length() - 1);
        b.append("}");
        return b.toString();
    }

    public void benchmark(int repetitions,int mapSize) {
        String values = generateValues(mapSize);
        System.out.println("Ranking expression parsing");
        System.out.println("  warming up");
        rankingExpressionParserParse(1000, values, mapSize);
        long startTime = System.currentTimeMillis();
        System.out.println(  "starting ....");
        rankingExpressionParserParse(repetitions, values, mapSize);
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("  Total time: " + totalTime + " ms, time per expression: " + (totalTime*1000/repetitions) + " microseconds");
    }

    private void rankingExpressionParserParse(int repetitions, String values, int expectedMapSize) {
        Map<String,Double> map = new HashMap<>();
        for (int i=0; i<repetitions; i++) {
            rankingExpressionParserParse(values, map);
            if ( map.size()!=expectedMapSize)
                throw new RuntimeException("Expected size: " + expectedMapSize + ", actual size: " + map.size());
            map.clear();
        }
    }
    private Map<String,Double> rankingExpressionParserParse(String values, Map<String,Double> map) {
        return new DoubleMapParser().parse(values,map);
    }

    public static void main(String[] args) {
        new MapParserMicroBenchmark().benchmark(100*1000,1000);
    }

    private static class DoubleMapParser extends MapParser<Double> {

        @Override
        protected Double parseValue(String s) {
            return Double.parseDouble(s);
        }

    }

}
