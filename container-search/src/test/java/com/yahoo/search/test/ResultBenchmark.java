// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

/**
 * Tests the speed of accessing hits in the query by id
 *
 * @author bratseth
 */
public class ResultBenchmark {

    public void run() {
        int foundCount=0;

        // Warm-up
        out("Warming up...");
        Result result=createResult();
        for (int i=0; i<10*1000; i++)
            foundCount+=accessResultFiveTimes(result);
        foundCount=0;

        long startTime=System.currentTimeMillis();
        out("Running...");
        for (int i=0; i<200*1000; i++)
            foundCount+=accessResultFiveTimes(result);
        out("Successfully looked up " + foundCount + " hits");
        long endTime=System.currentTimeMillis();
        out("Accessing a result 1.000.000 times took " + (endTime-startTime) + " ms");
    }

    private final Result createResult() {
        // 8 sets, 8 gets
        Result result=new Result(new Query("?query=test&hits=10&presentation.bolding=true&model.type=all"));
        addHits(5,"firstTopLevel",result.hits());
        result.hits().add(addHits(10, "group1hit", new HitGroup()));
        addHits(5, "secondTopLevel", result.hits());
        result.hits().add(addHits(10, "group2hit", new HitGroup()));
        result.hits().add(addHits(10, "group3hit", new HitGroup()));
        return result;
    }

    private final HitGroup addHits(int count,String idPrefix,HitGroup to) {
        for (int i=1; i<=count; i++)
            to.add(new Hit(idPrefix + i,1/i));
        return to;
    }

    private final int accessResultFiveTimes(Result result) {
        // 8 sets, 8 gets
        int foundCount=0;
        if (null!=result.hits().get("firstTopLevel1"))
            foundCount++;
        if (null!=result.hits().get("secondTopLevel3"))
            foundCount++;
        if (null!=result.hits().get("group3hit5"))
            foundCount++;
        if (null!=result.hits().get("group1hit2"))
            foundCount++;
        if (null!=result.hits().get("group2hit4"))
            foundCount++;
        return foundCount;
    }

    private void out(String string) {
        System.out.println(string);
    }

    public static void main(String[] args) {
        new ResultBenchmark().run();
    }

}
