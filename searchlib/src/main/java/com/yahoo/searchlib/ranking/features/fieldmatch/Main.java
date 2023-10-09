// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

/**
 * Helper for computing metrics from the command line.
 */
public class Main {

    public static void main(String[] args) {
        FieldMatchMetricsComputer c=new FieldMatchMetricsComputer();
        String query=getQuery(args);
        String field=getField(args);
        if (query==null || field==null) {
            printUsage();
            return;
        }

        FieldMatchMetrics metrics = c.compute(query,field);
        System.out.println(metrics.toStringDump());
    }

    private static String getQuery(String[] args) {
        if (args.length<1) return null;
        if (args[0].equals("-h") || args[0].equals("-help")) return null;
        return args[0];
    }

    private static String getField(String[] args) {
        if (args.length<2) return null;
        return args[1];
    }

    private static void printUsage() {
        System.out.println("Computes the string segment match metrics of a query and field.");
        System.out.println("Usage: java -jar searchlib.jar query field");
        System.out.println("Author: bratseth");
    }

}
