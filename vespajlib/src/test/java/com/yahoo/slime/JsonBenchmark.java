// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.test.json.Jackson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Integer;

/**
 * @author baldersheim
 */
public class JsonBenchmark {
    private static byte [] createJson(int numElements) {
        Slime slime = new Slime();
        Cursor a = slime.setArray();
        for (int i=0; i < numElements; i++) {
            Cursor e = a.addObject();
            e.setString("key", "i");
            e.setLong("weight", i);
        }
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        JsonFormat json = new JsonFormat(false);
        try {
            json.encode(bs, slime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bs.toByteArray();
    }
    private static long benchmarkJacksonStreaming(byte [] json, int numIterations) {
        long count = 0;
        JsonFactory jsonFactory = new JsonFactory();

        try {
            for (int i=0; i < numIterations; i++) {
                try (JsonParser jsonParser = jsonFactory.createParser(json)) {
                    JsonToken array = jsonParser.nextToken();
                    for (JsonToken token = jsonParser.nextToken(); !JsonToken.END_ARRAY.equals(token); token = jsonParser.nextToken()) {
                        if (JsonToken.FIELD_NAME.equals(token) && "weight".equals(jsonParser.getCurrentName())) {
                            token = jsonParser.nextToken();
                            count += jsonParser.getLongValue();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return count;
    }
    private static long benchmarkJacksonTree(byte [] json, int numIterations) {
        long count = 0;
        // use the ObjectMapper to read the json string and create a tree
        var mapper = Jackson.mapper();
        try {
            for (int i=0; i < numIterations; i++) {
                JsonNode node = mapper.readTree(json);
                for(JsonNode item : node) {
                    count += item.get("weight").asLong();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return count;
    }
    private static long benchmarkSlime(byte [] json, int numIterations) {
        long count = 0;
        for (int i=0; i < numIterations; i++) {
            JsonDecoder decoder = new JsonDecoder();
            Slime slime = decoder.decode(new Slime(), json);

            Cursor array = slime.get();
            int weightSymbol = slime.lookup("weight");
            for (int j=0, m=slime.get().children(); j < m; j++) {
                count += array.entry(j).field(weightSymbol).asLong();
            }
        }
        return count;
    }
    private static void warmup(byte [] json) {
        System.out.println(System.currentTimeMillis() + " Warming up");
        benchmarkSlime(json, 5000);
        System.out.println(System.currentTimeMillis() + " Done Warming up");
    }

    /**
     * jacksons 1000 40000 = 5.6 seconds
     * jacksont 1000 40000 = 11.0 seconds
     * slime 1000 40000  = 17.5 seconds
     * @param argv type, num elements in weigted set, num iterations
     */
    static public void main(String [] argv) {
        String type = argv[0];
        byte [] json = createJson(Integer.parseInt(argv[1]));
        warmup(json);
        int count = Integer.parseInt(argv[2]);
        System.out.println(System.currentTimeMillis() + " Start");
        long start = System.currentTimeMillis();
        long numValues;
        if ("jacksons".equals(type)) {
            numValues = benchmarkJacksonStreaming(json, count);
        } else if ("jacksont".equals(type)) {
            numValues = benchmarkJacksonTree(json, count);
        } else{
            numValues = benchmarkSlime(json, count);
        }
        System.out.println(System.currentTimeMillis() + " End with " + numValues + " values in " + (System.currentTimeMillis() - start) + " milliseconds.");
    }
}
