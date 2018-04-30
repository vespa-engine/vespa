// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch.test;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.ranking.features.fieldmatch.Field;
import com.yahoo.searchlib.ranking.features.fieldmatch.FieldMatchMetrics;
import com.yahoo.searchlib.ranking.features.fieldmatch.FieldMatchMetricsComputer;
import com.yahoo.searchlib.ranking.features.fieldmatch.FieldMatchMetricsParameters;
import com.yahoo.searchlib.ranking.features.fieldmatch.QueryTerm;
import com.yahoo.searchlib.ranking.features.fieldmatch.Query;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests of calculation of all the string match metrics.
 * Add true as the fourth parameter to assertMetrics to see a trace of what the test is doing.
 *
 * @author bratseth
 */
public class FieldMatchMetricsTestCase {

    @Test
    public void testOutOfOrder() {
        assertMetrics("outOfOrder:0","a","a");
        assertMetrics("outOfOrder:0","a b c","a b c");
        assertMetrics("outOfOrder:1","a b c","a c b");
        assertMetrics("outOfOrder:2","a b c","c b a");
        assertMetrics("outOfOrder:2","a b c d e","c x a b x x x x x e x x d");
        assertMetrics("outOfOrder:2","a b c d e","c x a b x x x x x e x x d");
        assertMetrics("outOfOrder:2", "a b c d e", "c x a b x x x x x e x x d");
    }

    @Test
    public void testSegments() {
        assertMetrics("segments:1","a","a");
        assertMetrics("segments:1","a b c","a b c");
        assertMetrics("segments:1","a b c","a x x b c");
        assertMetrics("segments:2","a b c","a x x x x x x x x x x x x x x x x x x x b c");
        assertMetrics("segments:2","a b c","b c x x x x x x x x x x x x x x x x x x x a");
        assertMetrics("segments:2 gaps:1","a b c","x x x a x x x x x x x x x x x x x x x x x x x b x x c x x");
        assertMetrics("segments:2 gaps:0 outOfOrder:0","a b c","b c x x x x x x x x x x x x x x x x x x x a");
        assertMetrics("segments:2 gaps:1","a b c","x x x b x x c x x x x x x x x x x x x x x x x x x x a x x");
        assertMetrics("segments:2 gaps:1","a y y b c","x x x b x x c x x x x x x x x x x x x x x x x x x x a x x");
    }

    @Test
    public void testGaps() {
        assertMetrics("gaps:0","a","a");
        assertMetrics("gaps:0","x�a","a");
        assertMetrics("gaps:0 gapLength:0","a b c","a b c");
        assertMetrics("gaps:1 gapLength:1","a b","b a");
        assertMetrics("gaps:1 gapLength:1","a b c","a x b c");
        assertMetrics("gaps:1 gapLength:3","a b c","a x X Xb c");
        assertMetrics("gaps:2 gapLength:2 outOfOrder:1","a b c","a c b");
        assertMetrics("gaps:2 gapLength:2 outOfOrder:0","a b c","a x b x c");
        assertMetrics("gaps:2 gapLength:5 outOfOrder:1","a b c","a x c x b");
        assertMetrics("gaps:3 outOfOrder:2 segments:1","a b c d e","x d x x b c x x a e");
        assertMetrics("gaps:0","y a b c","a b c x");
    }

    @Test
    public void testHead() {
        assertMetrics("head:0","a","a");
        assertMetrics("head:0","y","a");
        assertMetrics("head:1","a","x a");
        assertMetrics("head:2","a b c","x x a b c");
        assertMetrics("head:2","a b c","x x c x x a b");
        assertMetrics("head:2", "a b c", "x x c x x x x x x x x x x x x x x x a b");
    }

    @Test
    public void testTail() {
        assertMetrics("tail:0","a","a");
        assertMetrics("tail:0","y","a");
        assertMetrics("tail:1","a","a x");
        assertMetrics("tail:2","a b c","a b c x x");
        assertMetrics("tail:2","a b c","x x x c x x x x a b x x");
        assertMetrics("tail:0","a b c","x x c x x x x x x x x x x x x x x x a b");
    }

    @Test
    public void testLongestSequence() {
        assertMetrics("longestSequence:1","a","a");
        assertMetrics("longestSequence:1","a","a b c");
        assertMetrics("longestSequence:1","b","a b c");
        assertMetrics("longestSequence:3","a b c","x x a b c x x a b x");
        assertMetrics("longestSequence:3 segments:1","a b c","x x a b x x a b c x");
        assertMetrics("longestSequence:2","a b c d","x x c d x x a b x");
        assertMetrics("longestSequence:2","a b c d","x x a b x c d x x");
        assertMetrics("longestSequence:2","a b c d","x x a b x x x x x x x x x x x x x x x x x c d x x");
        assertMetrics("longestSequence:4 segments:1","a b c d","x x a b x x x x x x x x x x x x x x x x x c d x x a b c d");
    }

    @Test
    public void testMatches() {
        assertMetrics("matches:1 queryCompleteness:1   fieldCompleteness:1",   "a","a");
        assertMetrics("matches:3 queryCompleteness:1   fieldCompleteness:1",   "a b c","a b c");
        assertMetrics("matches:3 queryCompleteness:1   fieldCompleteness:0.5", "a b c","a b c a b d");
        assertMetrics("matches:3 queryCompleteness:0.5 fieldCompleteness:0.25","a y y b c y","a x x b c x a x a b x x");
    }

    @Test
    public void testCompleteness() {
        assertMetrics("completeness:1     queryCompleteness:1   fieldCompleteness:1",  "a","a");
        assertMetrics("completeness:0     queryCompleteness:0   fieldCompleteness:0",  "a","x");
        assertMetrics("completeness:0     queryCompleteness:0   fieldCompleteness:0",  "y","a");
        assertMetrics("completeness:0.975 queryCompleteness:1   fieldCompleteness:0.5","a","a a");
        assertMetrics("completeness:0.525 queryCompleteness:0.5 fieldCompleteness:1",  "a a","a");
        assertMetrics("completeness:1     queryCompleteness:1   fieldCompleteness:1",  "a b c","a b c");
        assertMetrics("completeness:0.525 queryCompleteness:0.5 fieldCompleteness:1",  "a b c d","a b");
        assertMetrics("completeness:0.975 queryCompleteness:1   fieldCompleteness:0.5","a b","a b c d");
        assertMetrics("completeness:0.97  queryCompleteness:1   fieldCompleteness:0.4","a b","a b c d e");
        assertMetrics("completeness:0.97  queryCompleteness:1   fieldCompleteness:0.4","a b","a b b b b");
    }

    @Test
    public void testOrderness() {
        assertMetrics("orderness:1",  "a","a");
        assertMetrics("orderness:1",  "a","x");
        assertMetrics("orderness:0",  "a a a","a"); // Oh well...
        assertMetrics("orderness:1",  "a","a a a");
        assertMetrics("orderness:0",  "a b","b a");
        assertMetrics("orderness:0.5","a b c","b a c");
        assertMetrics("orderness:0.5","a b c d","c b d x x x x x x x x x x x x x x x x x x x x x a");
        assertMetrics("orderness:1",  "a b","b x x x x x x x x x x x x x x x x x x x x x a");
    }

    @Test
    public void testRelatedness() {
        assertMetrics("relatedness:1",  "a","a");
        assertMetrics("relatedness:0",  "a","x");
        assertMetrics("relatedness:1",  "a b","a b");
        assertMetrics("relatedness:1",  "a b c","a b c");
        assertMetrics("relatedness:0.5","a b c","a b x x x x x x x x x x x x x x x x x x x x x x x c");
        assertMetrics("relatedness:0.5","a y b y y y c","a b x x x x x x x x x x x x x x x x x x x x x x x c");
    }

    @Test
    public void testLongestSequenceRatio() {
        assertMetrics("longestSequenceRatio:1",  "a","a");
        assertMetrics("longestSequenceRatio:0",  "a","x");
        assertMetrics("longestSequenceRatio:1",  "a a","a");
        assertMetrics("longestSequenceRatio:1",  "a","a a");
        assertMetrics("longestSequenceRatio:1",  "a b","a b");
        assertMetrics("longestSequenceRatio:1",  "a y"," a x");
        assertMetrics("longestSequenceRatio:0.5","a b","a x b");
        assertMetrics("longestSequenceRatio:0.75","a b c d","x x a b x a x c d a b c x d x");
    }

    @Test
    public void testEarliness() {
        assertMetrics("earliness:1",     "a","a");
        assertMetrics("earliness:0",     "a","x");
        assertMetrics("earliness:1",     "a","a a a");
        assertMetrics("earliness:1",     "a a a","a");
        assertMetrics("earliness:0.8",   "b","a b c");
        assertMetrics("earliness:0.8",     "b","a b");
        assertMetrics("earliness:0.9091","a b c","x b c x x x x x a x x x");
        assertMetrics("earliness:0.2",   "a b c","x b c a x x x x a x x x x x x x a b c x x");
    }

    @Test
    public void testWeight() {
        assertMetrics("weight:1",     "a","a");
        assertMetrics("weight:0",     "y","a");
        assertMetrics("weight:0.3333","a a a","a");
        assertMetrics("weight:1",     "a","a a a");
        assertMetrics("weight:1",     "a b c","a b c");
        assertMetrics("weight:1",     "a b c","x x a b x a x c x x a b x c c x");

        assertMetrics("weight:0.3333","a b c","a");
        assertMetrics("weight:0.6667","a b c","a b");

        assertMetrics("weight:1",   "a b c!200","a b c");     // Best
        assertMetrics("weight:0.75","a b c!200","b c");    // Middle
        assertMetrics("weight:0.5", "a b c!200","a b");     // Worst

        assertMetrics("weight:1","a!300 b c!200","a b c"); // Best too

        assertMetrics("weight:1",  "a b c!50","a b c");   // Best
        assertMetrics("weight:0.6","a b c!50","b c");   // Worse
        assertMetrics("weight:0.4","a b c!50","b");     // Worse
        assertMetrics("weight:0.2","a b c!50","c");     // Worst
        assertMetrics("weight:0.8","a b c!50","a b");   // Middle

        assertMetrics("weight:1",  "a b c!0","a b c");  // Best
        assertMetrics("weight:0.5","a b c!0","b c");  // Worst
        assertMetrics("weight:1",  "a b c!0","a b");    // As good as best
        assertMetrics("weight:0",  "a b c!0","c");      // No contribution

        assertMetrics("weight:0","a!0 b!0","a b");
        assertMetrics("weight:0","a!0 b!0","");

        // The query also has other terms having a total weight of 300
        // so we add a weight parameter which is the sum of the weights of this query terms + 300
        assertMetrics("weight:0.25",  "a","a",400);
        assertMetrics("weight:0",     "y","a",400);
        assertMetrics("weight:0.1667","a a a","a",600);
        assertMetrics("weight:0.25",  "a","a a a",400);
        assertMetrics("weight:0.5",   "a b c","a b c",600);
        assertMetrics("weight:0.5",   "a b c","x x a b x a x c x x a b x c c x",600);

        assertMetrics("weight:0.1667","a b c","a",600);
        assertMetrics("weight:0.3333","a b c","a b",600);

        assertMetrics("weight:0.5714","a b c!200","a b c",700); // Best
        assertMetrics("weight:0.4286","a b c!200","b c",700);   // Middle
        assertMetrics("weight:0.2857","a b c!200","a b",700);   // Worst

        assertMetrics("weight:0.6667","a!300 b c!200","a b c",900); // Better than best

        assertMetrics("weight:0.4545","a b c!50","a b c",550); // Best
        assertMetrics("weight:0.2727","a b c!50","b c",550);   // Worse
        assertMetrics("weight:0.1818","a b c!50","b",550);     // Worse
        assertMetrics("weight:0.0909","a b c!50","c",550);     // Worst
        assertMetrics("weight:0.3636","a b c!50","a b",550);   // Middle

        assertMetrics("weight:0.4","a b c!0","a b c",500);  // Best
        assertMetrics("weight:0.2","a b c!0","b c",500);  // Worst
        assertMetrics("weight:0.4","a b c!0","a b",500);    // As good as best
        assertMetrics("weight:0",  "a b c!0","c",500);      // No contribution

        assertMetrics("weight:0","a!0 b!0","a b",300);
        assertMetrics("weight:0","a!0 b!0","",300);
    }

    /** Calculated the same way as weight */
    @Test
    public void testSignificance() {
        assertMetrics("significance:1",     "a","a");
        assertMetrics("significance:0",     "a","x");
        assertMetrics("significance:0.3333","a a a","a");
        assertMetrics("significance:1",     "a","a a a");
        assertMetrics("significance:1",     "a b c","a b c");
        assertMetrics("significance:1",     "a b c","x x a b x a x c x x a b x c c x");

        assertMetrics("significance:0.3333","a b c","a");
        assertMetrics("significance:0.6667","a b c","a b");

        assertMetrics("significance:1",   "a b c%0.2","a b c");     // Best
        assertMetrics("significance:0.75","a b c%0.2","b c");    // Middle
        assertMetrics("significance:0.5", "a b c%0.2","a b");     // Worst

        assertMetrics("significance:1","a%0.3 b c%0.2","a b c"); // Best too

        assertMetrics("significance:1",  "a b c%0.05","a b c");   // Best
        assertMetrics("significance:0.6","a b c%0.05","b c");   // Worse
        assertMetrics("significance:0.4","a b c%0.05","b");     // Worse
        assertMetrics("significance:0.2","a b c%0.05","c");     // Worst
        assertMetrics("significance:0.8","a b c%0.05","a b");   // Middle

        assertMetrics("significance:1",  "a b c%0","a b c");  // Best
        assertMetrics("significance:0.5","a b c%0","b c");    // Worst
        assertMetrics("significance:1",  "a b c%0","a b");    // As good as best
        assertMetrics("significance:0",  "a b c%0","c");      // No contribution

        assertMetrics("significance:0","a%0 b%0","a b");
        assertMetrics("significance:0","a%0 b%0","");

        // The query also has other terms having a total significance of 0.3
        // so we add a significance parameter which is the sum of the significances of this query terms + 0.3
        assertMetrics("significance:0.25",  "a","a",0.4f);
        assertMetrics("significance:0",     "y","a",0.4f);
        assertMetrics("significance:0.1667","a a a","a",0.6f);
        assertMetrics("significance:0.25",  "a","a a a",0.4f);
        assertMetrics("significance:0.5",   "a b c","a b c",0.6f);
        assertMetrics("significance:0.5",   "a b c","x x a b x a x c x x a b x c c x",0.6f);

        assertMetrics("significance:0.1667","a b c","a",0.6f);
        assertMetrics("significance:0.3333","a b c","a b",0.6f);

        assertMetrics("significance:0.5714","a b c%0.2","a b c",0.7f); // Best
        assertMetrics("significance:0.4286","a b c%0.2","b c",0.7f);   // Middle
        assertMetrics("significance:0.2857","a b c%0.2","a b",0.7f);   // Worst

        assertMetrics("significance:0.6667","a%0.3 b c%0.2","a b c",0.9f); // Better than best

        assertMetrics("significance:0.4545","a b c%0.05","a b c",0.55f); // Best
        assertMetrics("significance:0.2727","a b c%0.05","b c",0.55f);   // Worse
        assertMetrics("significance:0.1818","a b c%0.05","b",0.55f);     // Worse
        assertMetrics("significance:0.0909","a b c%0.05","c",0.55f);     // Worst
        assertMetrics("significance:0.3636","a b c%0.05","a b",0.55f);   // Middle

        assertMetrics("significance:0.4","a b c%0","a b c",0.5f);  // Best
        assertMetrics("significance:0.2","a b c%0","b c",0.5f);    // Worst
        assertMetrics("significance:0.4","a b c%0","a b",0.5f);    // As good as best
        assertMetrics("significance:0",  "a b c%0","c",0.5f);      // No contribution

        assertMetrics("significance:0","a%0 b%0","a b",0.3f);
        assertMetrics("significance:0","a%0 b%0","",0.3f);
    }

    @Test
    public void testImportance() {
        assertMetrics("importance:0.75","a b c",    "a x x b x c c c",600);
        assertMetrics("importance:0.85","a b!500 c","a x x b x c c c",1000);

        // Twice as common - twice as weighty, but total weight has the extra 300 - less than the previous
        assertMetrics("importance:0.7857","a b!200%0.05 c","a x x b x c c c",700);
        // Here higher importancy exactly offsets the lowered uniqueness
        assertMetrics("importance:0.85","a b!500%0.5 c","a x x b x c c c",1000);
    }

    @Test
    public void testOccurrence() {
        assertMetrics("occurrence:0","a","x");
        assertMetrics("occurrence:1","a","a");
        assertMetrics("occurrence:0","a a a","x");
        assertMetrics("occurrence:1","a a a","a");
        assertMetrics("occurrence:1","a a a","a a a");
        assertMetrics("occurrence:1","a a a","a a a a");
        assertMetrics("occurrence:0.3571","a","x x x a x x a x a x x x a a");
        assertMetrics("occurrence:1","a","a a a a a a a a a a a a a a");
        assertMetrics("occurrence:1","a b","a b b a a a a a b a a b a a");

        // tests going beyond the occurrence limit
        FieldMatchMetricsParameters parameters=new FieldMatchMetricsParameters();
        parameters.setMaxOccurrences(10);
        parameters.freeze();
        FieldMatchMetricsComputer c=new FieldMatchMetricsComputer(parameters);
        assertMetrics("occurrence:1",     "a b","a a a a a a a a a a b b",false,c);
        assertMetrics("occurrence:0.9231","a b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("occurrence:0.6",   "a b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("occurrence:1",     "a b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("occurrence:1",     "a b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length
    }

    @Test
    public void testAbsoluteOccurrence() {
        assertMetrics("absoluteOccurrence:0",  "a","x");
        assertMetrics("absoluteOccurrence:0.01","a","a");
        assertMetrics("absoluteOccurrence:0","a a a","x");
        assertMetrics("absoluteOccurrence:0.01",  "a a a","a");
        assertMetrics("absoluteOccurrence:0.03",  "a a a","a a a");
        assertMetrics("absoluteOccurrence:0.04",  "a a a","a a a a");
        assertMetrics("absoluteOccurrence:0.05","a","x x x a x x a x a x x x a a");
        assertMetrics("absoluteOccurrence:0.14","a","a a a a a a a a a a a a a a");
        assertMetrics("absoluteOccurrence:0.07","a b","a b b a a a a a b a a b a a");

        // tests going beyond the occurrence limit
        FieldMatchMetricsParameters parameters=new FieldMatchMetricsParameters();
        parameters.setMaxOccurrences(10);
        parameters.freeze();
        FieldMatchMetricsComputer c=new FieldMatchMetricsComputer(parameters);
        assertMetrics("absoluteOccurrence:0.6","a b","a a a a a a a a a a b b",false,c);
        assertMetrics("absoluteOccurrence:0.6","a b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("absoluteOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("absoluteOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("absoluteOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length
    }

    @Test
    public void testWeightedOccurrence() {
        assertMetrics("weightedOccurrence:0","a!200","x");
        assertMetrics("weightedOccurrence:1","a!200","a");
        assertMetrics("weightedOccurrence:0","a!200 a a","x");
        assertMetrics("weightedOccurrence:1","a!200 a a","a");
        assertMetrics("weightedOccurrence:1","a a a","a a a");
        assertMetrics("weightedOccurrence:1","a!200 a a","a a a a");
        assertMetrics("weightedOccurrence:0.3571","a!200","x x x a x x a x a x x x a a");
        assertMetrics("weightedOccurrence:1","a!200","a a a a a a a a a a a a a a");
        assertMetrics("weightedOccurrence:0.5","a b","a b b a a a a a b a a b a a");

        assertMetrics("weightedOccurrence:0.5714","a!200 b","a b b a a a a a b a a b a a");
        assertMetrics("weightedOccurrence:0.6753","a!1000 b","a b b a a a a a b a a b a a");  // Should be higher
        assertMetrics("weightedOccurrence:0.4286","a b!200","a b b a a a a a b a a b a a"); // Should be lower
        assertMetrics("weightedOccurrence:0.3061","a b!2000","a b b a a a a a b a a b a a"); // Should be even lower

        assertMetrics("weightedOccurrence:0.30","a b",    "a a b b b b x x x x");
        assertMetrics("weightedOccurrence:0.3333","a b!200","a a b b b b x x x x"); // More frequent is more important - higher
        assertMetrics("weightedOccurrence:0.2667","a!200 b","a a b b b b x x x x"); // Less frequent is more important - lower
        assertMetrics("weightedOccurrence:0.2667","a b!50", "a a b b b b x x x x"); // Same relative

        assertMetrics("weightedOccurrence:0","a!0 b!0", "a a b b b b x x x x");

        // tests going beyond the occurrence limit
        FieldMatchMetricsParameters parameters=new FieldMatchMetricsParameters();
        parameters.setMaxOccurrences(10);
        parameters.freeze();
        FieldMatchMetricsComputer c=new FieldMatchMetricsComputer(parameters);
        assertMetrics("weightedOccurrence:0.6","a b","a a a a a a a a a a b b",false,c);
        assertMetrics("weightedOccurrence:0.6","a b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("weightedOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("weightedOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("weightedOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length

        assertMetrics("weightedOccurrence:0.7333","a!200 b","a a a a a a a a a a b b",false,c);
        assertMetrics("weightedOccurrence:0.4667","a b!200","a a a a a a a a a a b b",false,c);
        assertMetrics("weightedOccurrence:0.7333","a!200 b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("weightedOccurrence:0.7333","a!200 b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("weightedOccurrence:1",     "a!200 b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("weightedOccurrence:1",     "a!200 b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length
    }

    @Test
    public void testWeightedAbsoluteOccurrence() {
        assertMetrics("weightedAbsoluteOccurrence:0",    "a!200","x");
        assertMetrics("weightedAbsoluteOccurrence:0.01", "a!200","a");
        assertMetrics("weightedAbsoluteOccurrence:0",    "a!200 a a","x");
        assertMetrics("weightedAbsoluteOccurrence:0.01", "a!200 a a","a");
        assertMetrics("weightedAbsoluteOccurrence:0.03", "a a a","a a a");
        assertMetrics("weightedAbsoluteOccurrence:0.04", "a!200 a a","a a a a");
        assertMetrics("weightedAbsoluteOccurrence:0.05", "a!200","x x x a x x a x a x x x a a");
        assertMetrics("weightedAbsoluteOccurrence:0.14", "a!200","a a a a a a a a a a a a a a");
        assertMetrics("weightedAbsoluteOccurrence:0.07","a b","a b b a a a a a b a a b a a");

        assertMetrics("weightedAbsoluteOccurrence:0.08",  "a!200 b","a b b a a a a a b a a b a a");
        assertMetrics("weightedAbsoluteOccurrence:0.0945","a!1000 b","a b b a a a a a b a a b a a");  // Should be higher
        assertMetrics("weightedAbsoluteOccurrence:0.06",  "a b!200","a b b a a a a a b a a b a a"); // Should be lower
        assertMetrics("weightedAbsoluteOccurrence:0.0429","a b!2000","a b b a a a a a b a a b a a"); // Should be even lower

        assertMetrics("weightedAbsoluteOccurrence:0.03", "a b",    "a a b b b b x x x x");
        assertMetrics("weightedAbsoluteOccurrence:0.0333","a b!200","a a b b b b x x x x"); // More frequent is more important - higher
        assertMetrics("weightedAbsoluteOccurrence:0.0267","a!200 b","a a b b b b x x x x"); // Less frequent is more important - lower
        assertMetrics("weightedAbsoluteOccurrence:0.0267","a b!50", "a a b b b b x x x x"); // Same relative

        assertMetrics("weightedAbsoluteOccurrence:0","a!0 b!0", "a a b b b b x x x x");

        // tests going beyond the occurrence limit
        FieldMatchMetricsParameters parameters=new FieldMatchMetricsParameters();
        parameters.setMaxOccurrences(10);
        parameters.freeze();
        FieldMatchMetricsComputer c=new FieldMatchMetricsComputer(parameters);
        assertMetrics("weightedAbsoluteOccurrence:0.6","a b","a a a a a a a a a a b b",false,c);
        assertMetrics("weightedAbsoluteOccurrence:0.6","a b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("weightedAbsoluteOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("weightedAbsoluteOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("weightedAbsoluteOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length

        assertMetrics("weightedAbsoluteOccurrence:0.7333","a!200 b","a a a a a a a a a a b b",false,c);
        assertMetrics("weightedAbsoluteOccurrence:0.4667","a b!200","a a a a a a a a a a b b",false,c);
        assertMetrics("weightedAbsoluteOccurrence:0.7333","a!200 b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("weightedAbsoluteOccurrence:0.7333","a!200 b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("weightedAbsoluteOccurrence:1",     "a!200 b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("weightedAbsoluteOccurrence:1",     "a!200 b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length
    }

    @Test
    public void testSignificantOccurrence() {
        assertMetrics("significantOccurrence:0","a%0.2","x");
        assertMetrics("significantOccurrence:1","a%0.2","a");
        assertMetrics("significantOccurrence:0","a%0.2 a a","x");
        assertMetrics("significantOccurrence:1","a%0.2 a a","a");
        assertMetrics("significantOccurrence:1","a a a","a a a");
        assertMetrics("significantOccurrence:1","a%0.2 a a","a a a a");
        assertMetrics("significantOccurrence:0.3571","a%0.2","x x x a x x a x a x x x a a");
        assertMetrics("significantOccurrence:1","a%0.2","a a a a a a a a a a a a a a");
        assertMetrics("significantOccurrence:0.5","a b","a b b a a a a a b a a b a a");

        assertMetrics("significantOccurrence:0.5714","a%0.2 b","a b b a a a a a b a a b a a");
        assertMetrics("significantOccurrence:0.6753","a%1 b","a b b a a a a a b a a b a a");  // Should be higher
        assertMetrics("significantOccurrence:0.4286","a b%0.2","a b b a a a a a b a a b a a"); // Should be lower
        assertMetrics("significantOccurrence:0.3247","a b%1","a b b a a a a a b a a b a a"); // Should be even lower

        assertMetrics("significantOccurrence:0.30","a b",    "a a b b b b x x x x");
        assertMetrics("significantOccurrence:0.3333","a b%0.2","a a b b b b x x x x"); // More frequent is more important - higher
        assertMetrics("significantOccurrence:0.2667","a%0.2 b","a a b b b b x x x x"); // Less frequent is more important - lower
        assertMetrics("significantOccurrence:0.2667","a b%0.05", "a a b b b b x x x x"); // Same relative

        assertMetrics("significantOccurrence:0","a%0 b%0", "a a b b b b x x x x");

        // tests going beyond the occurrence limit
        FieldMatchMetricsParameters parameters=new FieldMatchMetricsParameters();
        parameters.setMaxOccurrences(10);
        parameters.freeze();
        FieldMatchMetricsComputer c=new FieldMatchMetricsComputer(parameters);
        assertMetrics("significantOccurrence:0.6","a b","a a a a a a a a a a b b",false,c);
        assertMetrics("significantOccurrence:0.6","a b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("significantOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("significantOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("significantOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length

        assertMetrics("significantOccurrence:0.7333","a%0.2 b","a a a a a a a a a a b b",false,c);
        assertMetrics("significantOccurrence:0.4667","a b%0.2","a a a a a a a a a a b b",false,c);
        assertMetrics("significantOccurrence:0.7333","a%0.2 b","a a a a a a a a a a a b b",false,c); // Starting to cut off
        assertMetrics("significantOccurrence:0.7333","a%0.2 b","a a a a a a a a a a a a a a a a a a a a a b b",false,c); // Way beyond cutoff for a
        assertMetrics("significantOccurrence:1",     "a%0.2 b","a a a a a a a a a a b b b b b b b b b b",false,c); // Exactly no cutoff
        assertMetrics("significantOccurrence:1",     "a%0.2 b","a a a a a a a a a a a b b b b b b b b b b b",false,c); // Field is too large to consider field length
    }

    @Test
    public void testUnweightedProximity() {
        assertMetrics("unweightedProximity:1",    "a","a");
        assertMetrics("unweightedProximity:1",    "a b c","a b c");
        assertMetrics("unweightedProximity:1",    "a b c","a b c x");
        assertMetrics("unweightedProximity:1",    "y a b c","a b c x");
        assertMetrics("unweightedProximity:1", "y a b c", "a b c x");
        assertMetrics("unweightedProximity:0.855", "y a b c", "a b x c x");
        assertMetrics("unweightedProximity:0.750","y a b c","a b x x c x");
        assertMetrics("unweightedProximity:0.71", "y a b c","a x b x c x"); // Should be slightly worse than the previous one
        assertMetrics("unweightedProximity:0.605","y a b c","a x b x x c x");
        assertMetrics("unweightedProximity:0.53", "y a b c","a x b x x x c x");
        assertMetrics("unweightedProximity:0.5",  "y a b c","a x x b x x c x");
    }

    @Test
    public void testReverseProximity() {
        assertMetrics("unweightedProximity:0.33",  "a b","b a");
        assertMetrics("unweightedProximity:0.62",  "a b c","c a b");
        assertMetrics("unweightedProximity:0.585", "y a b c","c x a b");
        assertMetrics("unweightedProximity:0.33",  "a b c","c b a");
        assertMetrics("unweightedProximity:0.6875","a b c d e","a b d c e");
        assertMetrics("unweightedProximity:0.9275","a b c d e","a b x c d e");
    }

    @Test
    public void testProximity() {
        assertMetrics("absoluteProximity:0.1    proximity:1",     "a b","a b");
        assertMetrics("absoluteProximity:0.3    proximity:1",     "a 0.3:b","a b");
        assertMetrics("absoluteProximity:0.1    proximity:1",     "a 0.0:b","a b");
        assertMetrics("absoluteProximity:1      proximity:1",     "a 1.0:b","a b");
        assertMetrics("absoluteProximity:0.033  proximity:0.33",  "a b","b a");
        assertMetrics("absoluteProximity:0.0108 proximity:0.0359","a 0.3:b","b a"); // Should be worse than the previous one
        assertMetrics("absoluteProximity:0.1    proximity:1",     "a 0.0:b","b a");
        assertMetrics("absoluteProximity:0      proximity:0",     "a 1.0:b","b a");

        // proximity with connextedness
        assertMetrics("absoluteProximity:0.0605 proximity:0.605", "a b c","a x b x x c");
        assertMetrics("absoluteProximity:0.0701 proximity:0.2003","a 0.5:b 0.2:c","a x b x x c"); // Most important is close, less important is far: Better
        assertMetrics("absoluteProximity:0.0605 proximity:0.605", "a b c","a x x b x c");
        assertMetrics("absoluteProximity:0.0582 proximity:0.1663","a 0.5:b 0.2:c","a x x b x c"); // Most important is far, less important is close: Worse

        assertMetrics("absoluteProximity:0.0727 proximity:0.7267","a b c d","a b x x x x x c d");
        assertMetrics("absoluteProximity:0.1    proximity:1",     "a b 0:c d","a b x x x x x c d"); // Should be better because the gap is unimportant
    }

    /**
     * Tests exactness (using field exactness only - nothing additional of interest to test with query exactness
     * as that is just another number multiplied with the term exactness)
     */
    @Test
    public void testExactness() {
        assertMetrics("exactness:1",     "a b c","a x b x x c");
        assertMetrics("exactness:0.9",   "a b c","a x b:0.7 x x c");
        assertMetrics("exactness:0.7",   "a b c","a x b:0.6 x x c:0.5");
        assertMetrics("exactness:0.775", "a!200 b c","a x b:0.6 x x c:0.5");
        assertMetrics("exactness:0.65",  "a b c!200","a x b:0.6 x x c:0.5");
    }

    @Test
    public void testMultiSegmentProximity() {
        assertMetrics("absoluteProximity:0.1    proximity:1",  "a b c",  "a b x x x x x x x x x x x x x x x x x x x x x x c");
        assertMetrics("absoluteProximity:0.05   proximity:0.5","a b c",  "a x x b x x x x x x x x x x x x x x x x x x x x x x c");
        assertMetrics("absoluteProximity:0.075  proximity:0.75","a b c d","a x x b x x x x x x x x x x x x x x x x x x x x x x c d");
    }

    @Test
    public void testSegmentDistance() {
        assertMetrics("segmentDistance:13 absoluteProximity:0.1",  "a b c","a b x x x x x x x x x x c");
        assertMetrics("segmentDistance:13 absoluteProximity:0.5",  "a 0.5:b c","a b x x x x x x x x x x c");
        assertMetrics("segmentDistance:13 absoluteProximity:0.1",  "a b c","b c x x x x x x x x x x a");
        assertMetrics("segmentDistance:25 absoluteProximity:0.1",  "a b c","b x x x x x x x x x x x a x x x x x x x x x x c");
        assertMetrics("segmentDistance:13 absoluteProximity:0.006","a b c","a x x x x x x x x x x x b x x x x x x x x c");
        assertMetrics("segmentDistance:24 absoluteProximity:0.1",  "a b c","a x x x x x x x x x x x b x x x x x x x x x c");
        assertMetrics("segmentDistance:25 absoluteProximity:0.1",  "a b c","a x x x x x x x x x x x b x x x x x x x x x x c");
        assertMetrics("segmentDistance:25 absoluteProximity:0.1",  "a b c","c x x x x x x x x x x x b x x x x x x x x x x a");
    }

    @Test
    public void testSegmentProximity() {
        assertMetrics("segmentProximity:1",  "a","a");
        assertMetrics("segmentProximity:0",  "a","x");
        assertMetrics("segmentProximity:1",  "a","a x");
        assertMetrics("segmentProximity:0",  "a b","a x x x x x x x x x x x x x x x x x x x x x x x b");
        assertMetrics("segmentProximity:0.4","a b","a x x x x x x x x x x x x x x x x x x x x x x b x x x x x x x x x x x x x x x x");
        assertMetrics("segmentProximity:0",  "a b c","a b x x x x x x x x x x x x x x x x x x x x x c");
        assertMetrics("segmentProximity:0.4","a b c","a b x x x x x x x x x x x x x x x x x x x x x c x x x x x x x x x x x x x x x x");
        assertMetrics("segmentProximity:0.4","a b c","b c x x x x x x x x x x x x x x x x x x x x x a x x x x x x x x x x x x x x x x");
    }

    /** Test cases where we choose between multiple different segmentations */
    @Test
    public void testSegmentSelection() {
        assertMetrics("segments:2 absoluteProximity:0.1 proximity:1 segmentStarts:19,41",
                      "a b c d e","x a b x c x x x x x x x x x x x x x x a b c x x x x x x x x x e x d x c d x x x c d e");
        //                         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
        //                         0                   1                   2                   3                   4
        // Should choose                                                 - - -                                       - -

        // Same as above but best matching segment have too low exactness
        assertMetrics("segments:2 absoluteProximity:0.0903 proximity:0.9033 segmentStarts:1,41",
                      "a b c d e","x a b x c x x x x x x x x x x x x x x a:0.2 b:0.3 c:0.4 x x x x x x x x x e x d x c d x x x c d e");
        //                         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9     0     1     2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
        //                         0                   1                   2                   3                   4
        // Should choose             - -   -                                                                                     - -

        assertMetrics("segments:1 absoluteProximity:0.0778 proximity:0.778","a b c d e f","x x a b b b c f e d a b c d x e x x x x x f d e f a b c a a b b c c d d e e f f");

        // Prefer one segment with ok proximity over two segments with great proximity
        assertMetrics("segments:1 segmentStarts:0","a b c d","a b x c d x x x x x x x x x x x a b x x x x x x x x x x x c d");
        assertMetrics("segments:1 segmentStarts:0","a b c d","a b x x x x x x x x c d x x x x x x x x x x x a b x x x x x x x x x x x c d");
    }

    @Test
    public void testMoreThanASegmentLengthOfUnmatchedQuery() {
        assertMetrics("absoluteProximity:0.1 proximity:1","a b y y y y y y y y y y y y y y y","a b");
        assertMetrics("segments:2 absoluteProximity:0.1 proximity:1","a b c d y y y y y y y y y y y y y y y","a b x x x x x x x x x x x x x x x x x x c d");
        assertMetrics("segments:2 absoluteProximity:0.1 proximity:1","a b y y y y y y y y y y y y y y y c d","a b x x x x x x x x x x x x x x x x x x c d");
    }

    @Test
    public void testQueryRepeats() {
        // Not really handled perfectly, but good enough
        assertMetrics("absoluteProximity:0.1    proximity:1      head:0 tail:0",            "a a a","a");
        assertMetrics("absoluteProximity:0.1    proximity:1      head:0 tail:0 gapLength:0","a a b c c","a a b c c");
        assertMetrics("absoluteProximity:0.1    proximity:1      head:0 tail:0 gapLength:0","a a b c c","a b c");
        assertMetrics("absoluteProximity:0.1    proximity:1      head:0 tail:0 gapLength:0","a b a b","a b a b");
        assertMetrics("absoluteProximity:0.0903 proximity:0.9033 head:0 tail:0 gapLength:1","a b a b","a b x a b");
        // Both terms take the same segment:
        assertMetrics("absoluteProximity:0.1 proximity:1 segments:2 gapLength:0 head:3 tail:18","a a","x x x a x x x x x x x x x x x x x x a x x x");
        // But not when the second is preferable
        assertMetrics("absoluteProximity:0.1 proximity:1 segments:2 gapLength:0 head:3 tail:3","a b b a","x x x a b x x x x x x x x x x x x x x b a x x x");

        assertMetrics("matches:2 fieldCompleteness:1","a b b b","a b");
    }

    @Test
    public void testZeroCases() {
        assertMetrics("absoluteProximity:0.1 proximity:1 matches:0 exactness:0","y","a");
        assertMetrics("absoluteProximity:0.1 proximity:1 matches:0 exactness:0","a","x");
        assertMetrics("absoluteProximity:0.1 proximity:1 matches:0 exactness:0","","x");
        assertMetrics("absoluteProximity:0.1 proximity:1 matches:0 exactness:0","y","");
        assertMetrics("absoluteProximity:0.1 proximity:1 matches:0 exactness:0","","");
    }

    @Test
    public void testExceedingIterationLimit() {
        {   // Segments found: a x x b   and   c d
            FieldMatchMetricsParameters p=new FieldMatchMetricsParameters();
            p.setMaxAlternativeSegmentations(0);
            FieldMatchMetricsComputer m=new FieldMatchMetricsComputer(p);
            assertMetrics("matches:4 tail:0 proximity:0.75 absoluteProximity:0.075","a b c d","a x x b x x x a x b x x x x x a b x x x x x x x x x x x x x x x x x c d",false,m);
        }

        {   // Segments found: a x b   and   c d
            FieldMatchMetricsParameters p=new FieldMatchMetricsParameters();
            p.setMaxAlternativeSegmentations(1);
            FieldMatchMetricsComputer m=new FieldMatchMetricsComputer(p);
            assertMetrics("matches:4 tail:0 proximity:0.855 absoluteProximity:0.0855","a b c d","a x x b x x x a x b x x x x x a b x x x x x x x x x x x x x x x x x c d",false,m);
        }

        {   // Segments found: a b   and   c d
            FieldMatchMetricsParameters p=new FieldMatchMetricsParameters();
            p.setMaxAlternativeSegmentations(2);
            FieldMatchMetricsComputer m=new FieldMatchMetricsComputer(p);
            assertMetrics("matches:4 tail:0 proximity:1 absoluteProximity:0.1","a b c d","a x x b x x x a x b x x x x x a b x x x x x x x x x x x x x x x x x c d",false,m);
        }
    }

    @Test
    public void testMatch() {
        // Ordered by decreasing match score per query
        assertMetrics("match:1",     "a","a");
        assertMetrics("match:0.9339","a","a x");
        assertMetrics("match:0",     "a","x");
        assertMetrics("match:0.9243","a","x a");
        assertMetrics("match:0.9025","a","x a x");

        assertMetrics("match:1",     "a b","a b");
        assertMetrics("match:0.9558","a b","a b x");
        assertMetrics("match:0.9463","a b","x a b");
        assertMetrics("match:0.1296","a b","a x x x x x x x x x x x x x x x x x x x x x x b");
        assertMetrics("match:0.1288","a b","a x x x x x x x x x x x x x x x x x x x x x x x x x x x b");

        assertMetrics("match:0.8647","a b c","x x a x b x x x x x x x x a b c x x x x x x x x c x x");
        assertMetrics("match:0.861", "a b c","x x a x b x x x x x x x x x x a b c x x x x x x c x x");
        assertMetrics("match:0.4869","a b c","a b x x x x x x x x x x x x x x x x x x x x x x c x x");
        assertMetrics("match:0.4853","a b c","x x a x b x x x x x x x x x x b a c x x x x x x c x x");
        assertMetrics("match:0.3621","a b c","a x b x x x x x x x x x x x x x x x x x x x x x c x x");
        assertMetrics("match:0.3619","a b c","x x a x b x x x x x x x x x x x x x x x x x x x c x x");
        assertMetrics("match:0.3584","a b c","x x a x b x x x x x x x x x x x x x x x x x x x x x c");
        assertMetrics("match:0.3474","a b c","x x a x b x x x x x x x x x x x x x x b x x x b x b x");
        assertMetrics("match:0.3421","a b c","x x a x b x x x x x x x x x x x x x x x x x x x x x x");
        assertMetrics("match:0.305" ,"a b c","x x a x b:0.7 x x x x x x x x x x x x x x x x x x x x x x");
        assertMetrics("match:0.2927","a b!200 c","x x a x b:0.7 x x x x x x x x x x x x x x x x x x x x x x");
    }

    @Test
    public void testRepeatedMatch() {
        // gap==1 caused by finding two possible segments due to repeated matching
        assertMetrics("fieldCompleteness:1 queryCompleteness:0.6667 segments:1 earliness:1 gaps:1",
                      "pizza hut pizza","pizza hut");
    }

    /** Three segments - improving the score on the first should impact the last */
    @Test
    public void testNestedAlternatives() {
        assertMetrics("segmentStarts:6,19,32 proximity:1",
                      "a b c d e f",
                      "a x b x x x a b x x x x x x x x x x x c d x x x x x x x x x x x e f");
        assertMetrics("segmentStarts:6,19,47 proximity:1",
                      "a b c d e f",
                      "a x b x x x a b x x x x x x x x x x x c d x x x x x x x x x x x e x f x x x x x x x x x x x x e f");
    }

    /** Nice demonstration of the limitations of this algorithm: Segment end points are determined greedily */
    @Test
    public void testSegmentationGreedyness() {
        assertMetrics("match:0.3717","a b c","a x b x x x x x x x x b c");
        assertMetrics("match:0.4981","a b c","a x z x x x x x x x x b c");
    }

    protected void assertMetrics(String correctSpec, String query, String field) {
        assertMetrics(correctSpec, query, field, false);
    }

    protected void assertMetrics(String correctSpec, String queryString, String field, int totalTermWeight) {
        Query query=toQuery(queryString);
        query.setTotalTermWeight(totalTermWeight);
        assertMetrics(correctSpec, query, toField(field), false, new FieldMatchMetricsComputer());
    }

    protected void assertMetrics(String correctSpec, String queryString, String field, float totalSignificance) {
        Query query=toQuery(queryString);
        query.setTotalSignificance(totalSignificance);
        assertMetrics(correctSpec, query, toField(field), false, new FieldMatchMetricsComputer());
    }

    protected void assertMetrics(String correctSpec,String query,String field,boolean printTrace) {
        assertMetrics(correctSpec,query,field,printTrace,new FieldMatchMetricsComputer());
    }

    protected void assertMetrics(String correctSpec,String query,String field,boolean printTrace,FieldMatchMetricsComputer m) {
        assertMetrics(correctSpec, toQuery(query), toField(field), printTrace, m);
    }

    protected void assertMetrics(String correctSpec, Query query, Field field, boolean printTrace, FieldMatchMetricsComputer m) {
        FieldMatchMetrics metrics = m.compute(query, field, printTrace);
        if (printTrace)
            System.out.println(metrics.trace());

        if (printTrace)
            System.out.println(metrics.toStringDump());

        for (String correctValueSpec: correctSpec.split(" ")) {
            if (correctValueSpec.trim().equals("")) continue;
            String metricName=correctValueSpec.split(":")[0];
            String correctValueString=correctValueSpec.split(":")[1];
            if (metricName.equals("segmentStarts")) {
                String[] correctSegmentStarts=correctValueString.split(",");
                List<Integer> segmentStarts=metrics.getSegmentStarts();
                assertEquals("Segment start count",correctSegmentStarts.length,segmentStarts.size());
                for (int i=0; i<segmentStarts.size(); i++)
                    assertEquals("Expected segment starts " + correctValueString + " was " + segmentStarts,
                                 Integer.valueOf(correctSegmentStarts[i]),segmentStarts.get(i));
            }
            else {
                float correctValue=Float.parseFloat(correctValueString);
                assertEquals(metricName, correctValue, (float)Math.round(metrics.get(metricName)*10000)/10000, 0.000000001);
            }
        }
    }

    private Query toQuery(String queryString) {
        if (queryString.length()==0) return new Query(new QueryTerm[0]);
        String[] queryTerms=queryString.split(" ");
        QueryTerm[] query=new QueryTerm[queryTerms.length];
        for (int i=0; i<query.length; i++) {
            String[] percentSplit=queryTerms[i].split("%");
            String[] bangSplit=percentSplit[0].split("!");
            String[] colonSplit=bangSplit[0].split(":");
            if (colonSplit.length>1)
                query[i]=new QueryTerm(colonSplit[1],Float.parseFloat(colonSplit[0]));
            else
                query[i]=new QueryTerm(colonSplit[0]);

            if (bangSplit.length>1)
                query[i].setWeight(Integer.parseInt(bangSplit[1]));
            if (percentSplit.length>1)
                query[i].setSignificance(Float.parseFloat(percentSplit[1]));
        }
        return new Query(query);
    }

    private Field toField(String fieldString) {
        if (fieldString.length() == 0) return new Field(ImmutableList.of());

        ImmutableList.Builder<Field.Term> terms = new ImmutableList.Builder<>();
        for (String termString : fieldString.split(" ")) {
            String[] colonSplit = termString.split(":");
            if (colonSplit.length > 1)
                terms.add(new Field.Term(colonSplit[0], Float.parseFloat(colonSplit[1])));
            else
                terms.add(new Field.Term(colonSplit[0]));
        }
        return new Field(terms.build());
    }

}
