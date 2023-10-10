// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.test;

import com.yahoo.search.Query;

/**
 * Tests the speed of accessing the query
 *
 * @author bratseth
 */
public class QueryBenchmark {

    public void run() {
        int result=0;

        // Warm-up
        out("Warming up...");
        for (int i=0; i<10*1000; i++)
            result+=createAndAccessQuery(i);

        long startTime=System.currentTimeMillis();
        out("Running...");
        for (int i=0; i<100*1000; i++)
            result+=createAndAccessQuery(i);
        out("Ignore this: " + result); // Make sure we are not fooled by optimization by creating an observable result
        long endTime=System.currentTimeMillis();
        out("Creating and accessing a query 100.000 times took " + (endTime-startTime) + " ms");
    }

    private final int createAndAccessQuery(int i) {
        // 8 sets, 8 gets

        Query query=new Query("?query=test&hits=10&presentation.bolding=true&model.type=all");
        query.properties().set("model.defaultIndex","title");
        query.properties().set("string1","value1:" + i);
        query.properties().set("string2","value2:" + i);
        query.properties().set("string3","value3:" + i);
        int result=((String)query.properties().get("string1")).length();
        result+=((String)query.properties().get("string2")).length();
        result+=((String)query.properties().get("string3")).length();
        result+=((String)query.properties().get("model.defaultIndex")).length();

        Query clone=query.clone();
        result+=((String)query.properties().get("string1")).length();
        result+=((String)query.properties().get("string2")).length();
        result+=((String)query.properties().get("string3")).length();
        result+=((String)clone.properties().get("model.defaultIndex")).length();
        return result;
    }

    private void out(String string) {
        System.out.println(string);
    }

    public static void main(String[] args) {
        new QueryBenchmark().run();
    }

}
