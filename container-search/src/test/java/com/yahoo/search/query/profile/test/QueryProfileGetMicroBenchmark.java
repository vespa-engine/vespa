// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;


/**
 * @author bratseth
 */
public class QueryProfileGetMicroBenchmark {

    private final String description;
    private final String propertyPrefix;
    private final boolean useDimensions;

    public QueryProfileGetMicroBenchmark(String description, String propertyPrefix, boolean useDimensions) {
        this.description=description;
        this.propertyPrefix=propertyPrefix;
        this.useDimensions=useDimensions;
    }

    public void benchmark(int count) {
        Query query=createQuery();
        getValues(100000,query); // warm-up
        System.out.println(description);
        long startTime=System.currentTimeMillis();
        getValues(count,query);
        long endTime=System.currentTimeMillis();
        long totalTime=(endTime-startTime);
        System.out.println("Done in " + totalTime + " ms (" + ((float)totalTime*1000/(count*2) + " microsecond per get)")); // *2 because we do 2 gets
    }

    private Query createQuery() {
        QueryProfile main = new QueryProfile("main");
        main.set("a", "value1", (QueryProfileRegistry)null);
        main.set("b", "value2", useDimensions ? new String[] {"x1"} : null, null);
        main.set("c", "value3", useDimensions ? new String[] {"x1","y2"} : null, null);
        main.freeze();
        Query query = new Query(HttpRequest.createTestRequest("?query=test&x=1&y=2", Method.GET), main.compile(null));
        setValues(query);
        return query;
    }

    private void setValues(Query query) {
        for (int i=0; i<10; i++) {
            String thisPrefix=propertyPrefix;
            if (thisPrefix==null)
                thisPrefix= "a"+i+".b"+i+".";
            query.properties().set(thisPrefix + "property" + i,"value" + i);
        }
    }

    private void getValues(int count,Query query) {
        final int dotInterval=10000000;
        CompoundName found = CompoundName.from(propertyPrefix + "property1");
        CompoundName notFound = CompoundName.from(propertyPrefix + "nonExisting");
        for (int i=0; i<count; i++) {
            if (count>dotInterval && i%(count/dotInterval)==0)
                System.out.print(".");
            if (null==query.properties().get(found))
                throw new RuntimeException("Expected value");
            if (null!=query.properties().get(notFound))
                throw new RuntimeException("Expected no value");
        }
    }

    public static void main(String[] args) {
        int count=10000000;
        new QueryProfileGetMicroBenchmark("Getting values in root, no dimensions                     ","",false).benchmark(count);
        System.out.println("");
        new QueryProfileGetMicroBenchmark("Getting values in 1-level nested profiles, no dimensions  ","a.",false).benchmark(count);
        System.out.println("");
        new QueryProfileGetMicroBenchmark("Getting values in 2-level nested profiles, no dimensions  ","a.b.",false).benchmark(count);
        System.out.println("");
        new QueryProfileGetMicroBenchmark("Getting values in root, with dimensions                   ","",true).benchmark(count);
        System.out.println("");
        new QueryProfileGetMicroBenchmark("Getting values in 1-level nested profiles, with dimensions","a.",true).benchmark(count);
        System.out.println("");
        new QueryProfileGetMicroBenchmark("Getting values in 2-level nested profiles, with dimensions","a.b.",true).benchmark(count);
        System.out.println("");
    }

}
