// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfileRegistry;

/**
 * @author bratseth
 */
public class QueryProfileCloneMicroBenchmark {

    private final String description;
    private final int propertyCount;
    private final String propertyPrefix;
    private final boolean useDimensions;

    public QueryProfileCloneMicroBenchmark(String description, int propertyCount, String propertyPrefix, boolean useDimensions) {
        this.description=description;
        this.propertyCount=propertyCount;
        this.propertyPrefix=propertyPrefix;
        this.useDimensions=useDimensions;
    }

    public void benchmark(int clone) {
        cloneQueryWithProfile(10000); // warm-up
        System.out.println(description);
        long startTime=System.currentTimeMillis();
        cloneQueryWithProfile(clone);
        long endTime=System.currentTimeMillis();
        long totalTime=(endTime-startTime);
        System.out.println("Done in " + totalTime + " ms (" + ((float)totalTime/clone + " ms per clone)"));
    }

    private void cloneQueryWithProfile(int clones) {
        QueryProfile main = new QueryProfile("main");
        main.set("a", "value1", (QueryProfileRegistry)null);
        main.set("b", "value2", useDimensions ? new String[] {"x1"} : null, null);
        main.set("c", "value3", useDimensions ? new String[] {"x1","y2"} : null, null);
        main.freeze();
        Query query = new Query(HttpRequest.createTestRequest("?query=test&x=1&y=2", Method.GET), main.compile(null));
        setValues(query);
        for (int i=0; i<clones; i++) {
            if (i%(clones/100)==0)
                System.out.print(".");
            query.clone();
        }
    }

    private void setValues(Query query) {
        for (int i=0; i<propertyCount; i++) {
            String thisPrefix=propertyPrefix;
            if (thisPrefix==null)
                thisPrefix="a"+i+".b"+i+".";
            query.properties().set(thisPrefix + "property" + i,"value" + i);
        }
    }

    public static void main(String[] args) {
        int count=100000;
        new QueryProfileCloneMicroBenchmark("Cloning a near-empty query                                                     ",0,"",false).benchmark(count);
        System.out.println("");
        new QueryProfileCloneMicroBenchmark("Cloning a query with 100 properties in root, no dimensions                     ",100,"",false).benchmark(count);
        System.out.println("");
        new QueryProfileCloneMicroBenchmark("Cloning a query with 100 properties in 1-level nested profiles, no dimensions  ",100,"a.",false).benchmark(count);
        System.out.println("");
        new QueryProfileCloneMicroBenchmark("Cloning a query with 100 properties in 2-level nested profiles, no dimensions  ",100,"a.b.",false).benchmark(count);
        System.out.println("");
        new QueryProfileCloneMicroBenchmark("Cloning a query with 100 properties in variable prefix profiles, no dimensions ",100,null,true).benchmark(count);
        System.out.println("");
        new QueryProfileCloneMicroBenchmark("Cloning a query with 100 properties in root, with dimensions                   ",100,"",true).benchmark(count);
        System.out.println("");
        new QueryProfileCloneMicroBenchmark("Cloning a query with 100 properties in 1-level nested profiles, with dimensions",100,"a.",true).benchmark(count);
        System.out.println("");
        new QueryProfileCloneMicroBenchmark("Cloning a query with 100 properties in 2-level nested profiles, with dimensions",100,"a.b.",true).benchmark(count);
        System.out.println("");
    }

}
