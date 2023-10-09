// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports the result of optimizations of a ranking expression.
 *
 * @author bratseth
 */
public class OptimizationReport {

    private Map<String,Integer> metrics=new LinkedHashMap<String,Integer>();

    private List<String> notes=new ArrayList<String>();

    public void setMetric(String name,int value) {
        metrics.put(name,value);
    }

    /** Returns the value of a metric, or null if it is not set */
    public int getMetric(String name) {
        return metrics.get(name);
    }

    /**
     * Increases the metric by the given name by increment, if the metric is not previously set,
     * this will assign it the value increment as expected
     */
    public void incMetric(String name,int increment) {
        Integer currentValue=metrics.get(name);
        if (currentValue==null)
            currentValue=0;
        metrics.put(name,currentValue+increment);
    }

    public void note(String note) {
        notes.add(note);
    }

    /** Returns all the content of this report as a multiline string */
    public String toString() {
        StringBuilder b=new StringBuilder();

        if (notes.size()>0) {
            b.append("Optimization notes:\n");
            List<String> displayedNotes=notes.subList(0,Math.min(5,notes.size()));
            for (String note : displayedNotes)
                b.append("   ").append(note).append("\n");
            if (notes.size()>displayedNotes.size())
                b.append("   ...\n");
        }

        b.append("Optimization metrics:\n");
        for (Map.Entry<String,Integer> metric : metrics.entrySet())
            b.append("   " + metric.getKey() + ": " + metric.getValue() + "\n");
        return b.toString();
    }

}
