// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.DimensionValues;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.test.QueryTestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class QueryProfileGetInComplexStructureMicroBenchmark {

    private final int dotDepth, variantCount, variantParentCount;

    public QueryProfileGetInComplexStructureMicroBenchmark(int dotDepth,int variantCount,int variantParentCount) {
        if (dotDepth<0) throw new IllegalArgumentException("dotDepth must be >=0");
        this.dotDepth=dotDepth;
        if (variantCount<1) throw new IllegalArgumentException("variantCount must be >0");
        this.variantCount=variantCount;
        if (variantParentCount<0) throw new IllegalArgumentException("varientParentCount must be >=0");
        this.variantParentCount=variantParentCount;
    }

    public void benchmark(int count, boolean useVariant) {
        QueryProfile main=createProfile(useVariant);
        Query query = new Query(QueryTestCase.httpEncode("?query=test&x=1&y=2"), main.compile(null));
        getValues(100000,query); // warm-up
        System.out.print(this + ": ");
        long startTime=System.currentTimeMillis();
        getValues(count,query);
        long endTime=System.currentTimeMillis();
        long totalTime=(endTime-startTime);
        System.out.println("Done in " + totalTime + " ms (" + ((float) totalTime * 1000 / (count * 2) + " microsecond per get)")); // *2 because we do 2 gets
    }

    private QueryProfile createProfile(boolean useVariant) {
        QueryProfile main=new QueryProfile("main");
        main.setDimensions(new String[] {"d0"});
        String prefix=generatePrefix();
        for (int i=0; i<variantCount; i++)
            main.set(prefix + "a","value-" + i, new String[] {"dv" + i}, null);
        for (int i=0; i<variantParentCount; i++) {
            main.addInherited(createParent(i), useVariant ? DimensionValues.createFrom(new String[] {"dv" + i}) : null);
        }
        main.freeze();
        return main;
    }

    private QueryProfile createParent(int i) {
        QueryProfile main=new QueryProfile("parent" + i);
        main.setDimensions(new String[] {"d0"});
        String prefix=generatePrefix();
        for (int j=0; j<variantCount; j++)
            main.set(prefix + "a","value-" + j + "-inherit" + i,new String[] {"dv" + j}, null);
        main.freeze();
        return main;
    }

    private void getValues(int count,Query query) {
        Map<String,String> dimensionValues=createDimensionValueMap();
        String prefix=generatePrefix();
        final int dotInterval=1000000;
        final CompoundName found = CompoundName.from(prefix + "a");
        final CompoundName notFound = CompoundName.from(prefix + "nonexisting");
        for (int i=0; i<count; i++) {
            if (count>dotInterval && i%(dotInterval)==0)
                System.out.print(".");
            if (null==query.properties().get(found,dimensionValues)) // request the last variant for worst case
                throw new RuntimeException("Expected value");
            if (null!=query.properties().get(notFound,dimensionValues)) // request the last variant for worst case
                throw new RuntimeException("Did not expect value");
        }
    }

    private Map<String,String> createDimensionValueMap() {
        Map<String,String> dimensionValueMap=new HashMap<>();
        dimensionValueMap.put("d0","dv" + (variantCount-1));
        return dimensionValueMap;
    }

    private String generatePrefix() {
        StringBuilder b=new StringBuilder();
        for (int i=0; i<dotDepth; i++)
            b.append("a.");
        return b.toString();
    }

    @Override
    public String toString() {
        return "dot depth: " + dotDepth + ", variant count: " + variantCount + ", variant parent count: " + variantParentCount;
    }

    private static void runBenchmarks(int count, boolean useVariants) {
        new QueryProfileGetInComplexStructureMicroBenchmark(1,1,1).benchmark(count, useVariants);
        new QueryProfileGetInComplexStructureMicroBenchmark(0,1,0).benchmark(count, useVariants);

        new QueryProfileGetInComplexStructureMicroBenchmark(9,1,0).benchmark(count, useVariants);
        new QueryProfileGetInComplexStructureMicroBenchmark(0,9,0).benchmark(count, useVariants);
        new QueryProfileGetInComplexStructureMicroBenchmark(9,9,0).benchmark(count, useVariants);
        new QueryProfileGetInComplexStructureMicroBenchmark(0,1,9).benchmark(count, useVariants);
        new QueryProfileGetInComplexStructureMicroBenchmark(9,1,9).benchmark(count, useVariants);
        new QueryProfileGetInComplexStructureMicroBenchmark(0,9,9).benchmark(count, useVariants);
        new QueryProfileGetInComplexStructureMicroBenchmark(9,9,9).benchmark(count, useVariants);
    }

    public static void main(String[] args) {
        System.out.println("Variant benchmarks");
        runBenchmarks(10000000, true);
        System.out.println("");
        System.out.println("Inheritance benchmarks");
        runBenchmarks(10000000, false);
        System.out.println("");
    }

}
