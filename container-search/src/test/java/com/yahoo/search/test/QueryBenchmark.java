// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.test;

import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;

import java.util.Map;

/**
 * Tests the speed of accessing the query
 *
 * @author bratseth
 */
public class QueryBenchmark {

    private final Map<String, String>  requestMap;
    private final CompiledQueryProfile queryProfile;

    QueryBenchmark() {
        requestMap = Map.of("a", "b",
                            "ranking.features.query(a)", "1.5",
                            "input.query(b)", "2.5");

        var registry = new QueryProfileRegistry();
        var profile = new QueryProfile("default");
        profile.set("ranking.features.query(c)", "3.5", registry);
        profile.set("input.query(b)", "4.5", registry);
        queryProfile = profile.compile(registry.compile());
    }

    public void run() {
        int result = 0;

        // Warm-up
        out("Warming up...");
        for (int i = 0; i < 100 * 1000; i++)
            result += createAndAccessQuery(i);

        long startTime = System.currentTimeMillis();
        out("Running...");
        int repetitions = 1000 * 1000;
        for (int i = 0; i < repetitions; i++)
            result += createAndAccessQuery(i);
        long endTime = System.currentTimeMillis();
        out("Creating and accessing a query takes " + ((endTime - startTime)*1000/repetitions) + " microseconds");
    }

    private int createAndAccessQuery(int i) {
        Query query = new Query.Builder().setRequestMap(requestMap)
                                         .setQueryProfile(queryProfile)
                                         .build();
        // 8 sets, 8 gets
        query.properties().set("model.defaultIndex","title");
        query.properties().set("string1","value1:" + i);
        query.properties().set("string2","value2:" + i);
        query.properties().set("string3","value3:" + i);
        int result=((String)query.properties().get("string1")).length();
        result += ((String)query.properties().get("string2")).length();
        result += ((String)query.properties().get("string3")).length();
        result += ((String)query.properties().get("model.defaultIndex")).length();

        Query clone = query.clone();
        result += ((String)query.properties().get("string1")).length();
        result += ((String)query.properties().get("string2")).length();
        result += ((String)query.properties().get("string3")).length();
        result += ((String)clone.properties().get("model.defaultIndex")).length();
        return result;
    }

    private void out(String string) {
        System.out.println(string);
    }

    public static void main(String[] args) {
        new QueryBenchmark().run();
    }

}
