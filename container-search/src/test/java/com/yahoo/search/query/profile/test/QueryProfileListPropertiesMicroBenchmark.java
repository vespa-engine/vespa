// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bratseth
 */
public class QueryProfileListPropertiesMicroBenchmark {

    private final String description;
    private final String propertyPrefix;
    private final boolean useDimensions;

    public QueryProfileListPropertiesMicroBenchmark(String description, String propertyPrefix, boolean useDimensions) {
        this.description=description;
        this.propertyPrefix=propertyPrefix;
        this.useDimensions=useDimensions;
    }

    public void benchmark(int count) {
        Query query=createQuery();
        listValues(10000, query); // warm-up
        System.out.println(description);
        long startTime=System.currentTimeMillis();
        listValues(count, query);
        long endTime=System.currentTimeMillis();
        long totalTime=(endTime-startTime);
        System.out.println("Done in " + totalTime + " ms (" + ((float)totalTime*1000/(count) + " microsecond per listProperties)"));
    }

    private Query createQuery() {
        Map<String,String> dimensions=null;
        if (useDimensions) {
            dimensions=new HashMap<>();
            dimensions.put("x","1");
            dimensions.put("y","2");
        }

        QueryProfile main=new QueryProfile("main");
        setValues(10,main,dimensions);
        QueryProfile parent=new QueryProfile("parent");
        setValues(5,main,dimensions);
        main.addInherited(parent);
        main.freeze();
        Query query = new Query(HttpRequest.createTestRequest("?query=test&x=1&y=2", Method.GET), main.compile(null));
        return query;
    }

    private void setValues(int count,QueryProfile profile,Map<String,String> dimensions) {
        for (int i=0; i<count; i++) {
            String thisPrefix=propertyPrefix;
            if ( ! thisPrefix.isEmpty())
                thisPrefix+=".";
            profile.set(thisPrefix + "property" + i, "value" + i, dimensions, null);
        }
    }

    private void listValues(int count,Query query) {
        final int dotInterval=1000000;
        for (int i=0; i<count; i++) {
            if (count>dotInterval && i%(count/dotInterval)==0)
                System.out.print(".");
            Map<String,Object> properties = query.properties().listProperties(propertyPrefix);
            int expectedSize = 10 + (propertyPrefix.isEmpty() ? 3 : 0); // 3 extra properties on the root
            if ( properties.size() != expectedSize )
                throw new RuntimeException("Expected a map of 10 elements, but got " + expectedSize + ": \n" + toString(properties));
        }
    }

    private String toString(Map<String,Object> map) {
        StringBuilder b=new StringBuilder();
        for (Map.Entry<String,Object> entry : map.entrySet())
            b.append("   ")
                .append(entry.getKey())
                .append(" = ")
                .append(entry.getValue().toString())
                .append("\n");
        return b.toString();
    }

    public static void main(String[] args) {
        int count=1000000;
        new QueryProfileListPropertiesMicroBenchmark("Listing values in root, no dimensions                     ","",false).benchmark(count);
        System.out.println("");
        new QueryProfileListPropertiesMicroBenchmark("Listing values in 1-level nested profiles, no dimensions  ","a",false).benchmark(count);
        System.out.println("");
        new QueryProfileListPropertiesMicroBenchmark("Listing values in 2-level nested profiles, no dimensions  ","a.b",false).benchmark(count);
        System.out.println("");
        new QueryProfileListPropertiesMicroBenchmark("Listing values in root, with dimensions                   ","",true).benchmark(count);
        System.out.println("");
        new QueryProfileListPropertiesMicroBenchmark("Listing values in 1-level nested profiles, with dimensions","a",true).benchmark(count);
        System.out.println("");
        new QueryProfileListPropertiesMicroBenchmark("Listing values in 2-level nested profiles, with dimensions","a.b",true).benchmark(count);
        System.out.println("");
    }

}
