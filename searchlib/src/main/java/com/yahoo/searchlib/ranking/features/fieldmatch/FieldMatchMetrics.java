// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Math.*;

/**
 * The collection of metrics calculated by the string match metric calculator.
 *
 * @author  bratseth
 */
public final class FieldMatchMetrics implements Cloneable {

    /** The calculator creating this - given on initialization */
    private final FieldMatchMetricsComputer source;

    /** The trace accumulated during execution - empty if no tracing */
    private final Trace trace = new Trace();

    private boolean complete;

    // Metrics
    private int outOfOrder;
    private int segments;
    private int gaps;
    private int gapLength;
    private int longestSequence;
    private int head;
    private int tail;
    private int matches;
    private float proximity;
    private float unweightedProximity;
    private float segmentDistance;
    private int pairs;
    private float weight;
    private float significance;
    private float occurrence;
    private float weightedOccurrence;
    private float absoluteOccurrence;
    private float weightedAbsoluteOccurrence;
    private float significantOccurrence;
    private float weightedExactnessSum;
    private int weightSum;

    // Temporary variables
    private int currentSequence;
    private List<Integer> segmentStarts=new ArrayList<>();
    private int queryLength;

    public FieldMatchMetrics(FieldMatchMetricsComputer source) {
        this.source=source;

        complete=false;

        outOfOrder = 0;
        segments = 0;
        gaps = 0;
        gapLength = 0;
        longestSequence = 1;
        head = -1;
        tail = -1;
        proximity = 0;
        unweightedProximity = 0;
        segmentDistance = 0;
        matches = 0;
        pairs = 0;
        weight = 0;
        significance = 0;
        weightedExactnessSum = 0;
        weightSum = 0;

        currentSequence=0;
        segmentStarts.clear();
        queryLength = source.getQuery().getTerms().length;
    }

    /** Are these metrics representing a complete match */
    public boolean isComplete() { return complete; }

    public void setComplete(boolean complete) { this.complete=complete; }

    /** Returns the segment start points */
    public List<Integer> getSegmentStarts() { return segmentStarts; }

    /**
     * Returns a metric by name
     *
     * @throws IllegalArgumentException if the metric name (case sensitive) is not present
     */
    public float get(String name) {
        try {
            Method getter = getClass().getMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
            return ((Number)getter.invoke(this)).floatValue();
        }
        catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No metric named '" + name + "' is known");
        }
        catch (Exception e) {
            throw new RuntimeException("Error getting metric '" + name + "'",e);
        }
    }

    // Base metrics ----------------------------------------------------------------------------------------------

    /** Returns the total number of out of order token sequences within field segments */
    public int getOutOfOrder() { return outOfOrder; }

    /** Returns the number of field text segments which are needed to match the query as completely as possible */
    public int getSegments() { return segments; }

    /** Returns the total number of position jumps (backward or forward) within document segments */
    public int getGaps() { return gaps; }

    /** Returns the summed size of all gaps within segments */
    public int getGapLength() { return gapLength; }

    /** Returns the size of the longest matched continuous, in-order sequence in the document */
    public int getLongestSequence() { return longestSequence; }

    /** Returns the number of tokens in the field preceding the start of the first matched segment */
    public int getHead() { return head; }

    /** Returns the number of tokens in the field following the end of the last matched segment */
    public int getTail() { return tail; }

    /** Returns the number of query terms which was matched in this field */
    public int getMatches() { return matches; }

    /** Returns the number of in-segment token pairs */
    public int getPairs() { return pairs; }

    /**
     * Returns the normalized proximity of the matched terms, weighted by the connectedness of the query terms.
     * This number is 0.1 if all the matched terms are and have default or lower connectedness, close to 1 if they
     * are following in sequence and have a high connectedness, and close to 0 if they are far from each other in the
     * segment or out of order
     */
    public float getAbsoluteProximity() {
        if (pairs < 1) return 0.1f;

        return proximity/pairs;
    }

    /**
     * Returns the normalized proximity of the matched terms, not taking term connectedness into account.
     * This number is close to 1 if all the matched terms are
     * following each other in sequence, and close to 0 if they are far from each other or out of order
     */
    public float getUnweightedProximity() {
        if (pairs < 1) return 1f;
        return unweightedProximity/pairs;
    }

    /**
     * Returns the sum of the distance between all segments making up a match to the query, measured
     * as the sum of the number of token positions separating the <i>start</i> of each field adjacent segment.
     */
    public float getSegmentDistance() { return segmentDistance; }

    /**
     * <p>Returns the normalized weight of this match relative to the whole query:
     * The sum of the weights of all <i>matched</i> terms/the sum of the weights of all <i>query</i> terms
     * If all the query terms were matched, this is 1. If no terms were matched, or these matches has weight zero,
     * this is 0.</p>
     *
     * <p>As the sum of this number over all the terms of the query is always 1, sums over all fields of
     * normalized rank features for each field multiplied by this number for the same field will produce a
     * normalized number.</p>
     *
     * <p>Note that this scales with the number of matched query terms in the field. If you want a component which does
     * not, divide by matches.</p>
     */
    public float getWeight() { return weight; }

    /**
     * <p>Returns the normalized term significance (1-frequency) of the terms of this match relative to the whole query:
     * The sum of the significance of all <i>matched</i> terms/the sum of the significance of all <i>query</i> terms
     * If all the query terms were matched, this is 1. If no terms were matched, or if the significance of all the matched terms
     * is zero (they are present in all (possible) documents), this number is zero.</p>
     *
     * <p>As the sum of this number over all the terms of the query is always 1, sums over all fields of
     * normalized rank features for each field multiplied by this number for the same field will produce a
     * normalized number.</p>
     *
     * <p>Note that this scales with the number of matched query terms in the field. If you want a component which does
     * not, divide by matches.</p>
     */
    public float getSignificance() { return significance; }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query.
     * This number is 1 if there are many occurrences of the query terms <i>in absolute terms,
     * or relative to the total content of the field</i>, and 0 if there are none.</p>
     *
     * <p>This is suitable for occurrence in fields containing regular text.</p>
     */
    public float getOccurrence() { return occurrence; }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query:
     *
     * <code>sum over all query terms(min(number of occurrences of the term,maxOccurrences))/(query term count*100)</code>
     *
     * <p>This number is 1 if there are many occurrences of the query terms, and 0 if there are none.
     * This number does not take the actual length of the field into account, so it is suitable for uses of occurrence
     * to denote importance across multiple terms.</p>
     */
    public float getAbsoluteOccurrence() { return absoluteOccurrence; }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query, weighted by term weight.
     * This number is close to 1 if there are many occurrences of highly weighted query terms,
     * in absolute terms, or relative to the total content of the field, and 0 if there are none.</p>
     */
    public float getWeightedOccurrence() { return weightedOccurrence; }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query, taking weights
     * into account so that occurrences of higher weighted query terms has more impact than lower weighted terms.</p>
     *
     * <p>This number is 1 if there are many occurrences of the highly weighted terms, and 0 if there are none.
     * This number does not take the actual length of the field into account, so it is suitable for uses of occurrence
     * to denote importance across multiple terms.</p>
     */
    public float getWeightedAbsoluteOccurrence() { return weightedAbsoluteOccurrence; }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query
     * <i>in absolute terms,
     * or relative to the total content of the field</i>, weighted by term significance.
     *
     * <p>This number is 1 if there are many occurrences of the highly significant terms, and 0 if there are none.</p>
     */
    public float getSignificantOccurrence() { return significantOccurrence; }

    /**
     * <p>Returns the degree to which the query terms submitted matched exactly terms contained in the document.
     * This is 1 if all the terms matched exactly, and closer to 0 as more of the terms was matched only as stem forms.
     * </p>
     *
     * <p>This is the query term weighted average of the exactness of each match, where the exactness of a match is
     * the product of the exactness of the matching query term and the matching field term:
     * <code>
     * sum over matching query terms(query term weight * query term exactness * field term exactness) /
     * sum over matching query terms(query term weight)
     * </code>
     */
    public float getExactness() {
        if (matches == 0) return 0;
        return weightedExactnessSum / weightSum;
    }

    // Derived metrics ----------------------------------------------------------------------------------------------

    /** The ratio of query tokens which was matched in the field: <code>matches/queryLength</code> */
    public float getQueryCompleteness() {
        return (float)matches/source.getQuery().getTerms().length;
    }

    /** The ratio of query tokens which was matched in the field: <code>matches/fieldLength</code> */
    public float getFieldCompleteness() {
        return (float)matches/source.getField().terms().size();
    }

    /**
     * Total completeness, where field completeness is more important:
     * <code>queryCompleteness * ( 1 - fieldCompletenessImportance) + fieldCompletenessImportance * fieldCompleteness</code>
     */
    public float getCompleteness() {
        float fieldCompletenessImportance = source.getParameters().getFieldCompletenessImportance();
        return getQueryCompleteness() * ( 1 - fieldCompletenessImportance) + fieldCompletenessImportance*getFieldCompleteness();
    }

    /** Returns how well the order of the terms agreed in segments: <code>1-outOfOrder/pairs</code> */
    public float getOrderness() {
        if (pairs == 0) return 1f;
        return 1-(float)outOfOrder/pairs;
    }

    /** Returns the degree to which different terms are related (occurring in the same segment): <code>1-segments/(matches-1)</code> */
    public float getRelatedness() {
        if (matches == 0) return 0;
        if (matches == 1) return 1;
        return 1 - (float)(segments - 1) / (matches - 1);
    }

    /** Returns <code>longestSequence/matches</code> */
    public float getLongestSequenceRatio() {
        if (matches == 0) return 0;
        return (float)longestSequence / matches;
    }

    /** Returns the closeness of the segments in the field: <code>1-segmentDistance/fieldLength</code> */
    public float getSegmentProximity() {
        if (matches == 0) return 0;
        return 1 - segmentDistance / source.getField().terms().size();
    }

    /**
     * Returns a value which is close to 1 when matched terms are close and close to zero when they are far apart
     * in the segment. Relatively more connected terms influence this value more.
     * This is absoluteProximity/average connectedness.
     */
    public float getProximity() {
        float totalConnectedness = 0;
        for (int i = 1; i < queryLength; i++) {
            totalConnectedness += (float)Math.max(0.1, source.getQuery().getTerms()[i].getConnectedness());
        }
        float averageConnectedness = 0.1f;
        if (queryLength > 1)
            averageConnectedness = totalConnectedness / (queryLength - 1);
        return getAbsoluteProximity() / averageConnectedness;
    }

    /**
     * <p>Returns the average of significance and weight.</p>
     *
     * <p>As the sum of this number over all the terms of the query is always 1, sums over all fields of
     * normalized rank features for each field multiplied by this number for the same field will produce a
     * normalized number.</p>
     *
     * <p>Note that this scales with the number of matched query terms in the field. If you want a component which does
     * not, divide by matches.</p>
     */
    public float getImportance() {
        return (getSignificance() + getWeight()) / 2;
    }

    /** A normalized measure of how early the first segment occurs in this field: <code>1-head/(max(6,field.length)-1)</code> */
    public float getEarliness() {
        if (matches == 0) return 0; // Covers field.length==0 too
        if (source.getField().terms().size() == 1) return 1;
        return 1 - (float)head/(max(6, source.getField().terms().size()) - 1);
    }

    /**
     * <p>A ready-to-use aggregate match score. Use this if you don't have time to find a better application specific
     * aggregate score of the fine grained match metrics.</p>
     *
     * <p>The current formula is
     *
     * <code>
     * ( proximityCompletenessImportance * (1-relatednessImportance + relatednessImportance*relatedness)
     *               proximity * exactness * completeness^2 + earlinessImportance * earliness + segmentProximityImportance * segmentProximity )
     * / (proximityCompletenessImportance + earlinessImportance + relatednessImportance)</code>
     *
     * but this is subject to change (i.e improvement) at any time.
     * </p>
     *
     *
     * <p>Weight and significance are not taken into account because this is meant to capture tha quality of the
     * match in this field, while those measures relate this match to matches in other fields. This number
     * can be multiplied with those values when combining with other field match scores.</p>
     */
    public float getMatch() {
        float proximityCompletenessImportance = source.getParameters().getProximityCompletenessImportance();
        float earlinessImportance = source.getParameters().getEarlinessImportance();
        float relatednessImportance = source.getParameters().getRelatednessImportance();
        float segmentProximityImportance = source.getParameters().getSegmentProximityImportance();
        float occurrenceImportance = source.getParameters().getOccurrenceImportance();
        float scaledRelatedness = 1 - relatednessImportance + relatednessImportance*getRelatedness();

        return ( proximityCompletenessImportance * scaledRelatedness * getProximity() * getExactness() * getCompleteness() * getCompleteness()
                 + earlinessImportance * getEarliness()
                 + segmentProximityImportance * getSegmentProximity()
                 + occurrenceImportance * getOccurrence())
               / (proximityCompletenessImportance + earlinessImportance + segmentProximityImportance + occurrenceImportance);
    }

    /**
     * <p>The metric use to select the best segments during execution of the string match metric algorithm.</p>
     *
     * <p>This metric, and any metric it depends on, must be correct each time a segment is completed,
     * not only when the metrics are complete, because this metric is used to choose segments during calculation.</p>
     */
    float getSegmentationScore() {
        if (segments == 0) return 0;
        return getAbsoluteProximity() * getExactness() / (segments * segments);
    }

    // Events emitted from the computer while matching strings  ----------------------------------------------------
    // Note that one move in the computer may cause multiple events

    // Events on single positions ----------

    /** Called once for every match */
    void onMatch(int i, int j) {
        if (matches >= source.getField().terms().size()) return;
        matches++;
        weight += (float)source.getQuery().getTerms()[i].getWeight() / source.getQuery().getTotalTermWeight();
        significance += source.getQuery().getTerms()[i].getSignificance() / source.getQuery().getTotalSignificance();
        int queryTermWeight = source.getQuery().getTerms()[i].getWeight();
        weightedExactnessSum += queryTermWeight * source.getQuery().getTerms()[i].getExactness() * source.getField().terms().get(j).exactness();
        weightSum += queryTermWeight;
    }

    /** Called once per sequence, when the sequence starts */
    void onSequenceStart(int j) {
        if (head==-1 || j<head)
            head=j;

        currentSequence=1;
    }

    /** Called once per sequence when the sequence ends */
    void onSequenceEnd(int j) {
        int sequenceTail = source.getField().terms().size() - j - 1;
        if (tail ==-1 || sequenceTail < tail)
            tail = sequenceTail;

        if (currentSequence > longestSequence)
            longestSequence = currentSequence;
        currentSequence = 0;
    }

    /** Called once when this value is calculated, before onComplete */
    void setOccurrence(float occurrence) { this.occurrence = occurrence; }

    /** Called once when this value is calculated, before onComplete */
    void setWeightedOccurrence(float weightedOccurrence) { this.weightedOccurrence = weightedOccurrence; }

    /** Called once when this value is calculated, before onComplete */
    void setAbsoluteOccurrence(float absoluteOccurrence) { this.absoluteOccurrence = absoluteOccurrence; }

    /** Called once when this value is calculated, before onComplete */
    void setWeightedAbsoluteOccurrence(float weightedAbsoluteOccurrence) { this.weightedAbsoluteOccurrence = weightedAbsoluteOccurrence; }

    /** Called once when this value is calculated, before onComplete */
    void setSignificantOccurrence(float significantOccurrence) { this.significantOccurrence = significantOccurrence; }

    /** Called once when matching is complete */
    void onComplete() {
        // segment distance - calculated from sorted segment starts
        if (segmentStarts.size() <= 1) {
            segmentDistance = 0;
        }
        else {
            Collections.sort(segmentStarts);
            for (int i = 1; i < segmentStarts.size(); i++) {
                segmentDistance += segmentStarts.get(i) - segmentStarts.get(i - 1) + 1;
            }
        }

        if (head == -1) head = 0;
        if (tail == -1) tail = 0;
    }

    // Events on pairs ----------

    /** Called when <i>any</i> pair is encountered */
    void onPair(int i, int j, int previousJ) {
        int distance = j - previousJ - 1;
        if (distance < 0) distance++; // Discontinuity where the two terms are in the same position
        if (abs(distance) > source.getParameters().getProximityLimit()) return; // Contribution=0

        // We have an in-segment pair
        float pairProximity = source.getParameters().getProximity(distance + source.getParameters().getProximityLimit());

        unweightedProximity += pairProximity;

        float connectedness = source.getQuery().getTerms()[i].getConnectedness();
        proximity += (float)pow(pairProximity, connectedness / 0.1) * (float)max(0.1, connectedness);

        pairs++;
    }

    /** Called when an in-sequence pair is encountered */
    void onInSequence(int i, int j, int previousJ) {
        currentSequence++;
    }

    /** Called when a gap (within a sequence) is encountered */
    void onInSegmentGap(int i, int j, int previousJ) {
        gaps++;
        if (j>previousJ) {
            gapLength+=abs(j-previousJ)-1; // gap length may be 0 if the gap was in the query
        }
        else {
            outOfOrder++;
            gapLength+=abs(j-previousJ);
        }
    }

    /**
     * Called when a new segment is started
     *
     * @param previousJ the end of the previous segment, or -1 if this is the first segment
     * */
    void onNewSegment(int i, int j, int previousJ) {
        segments++;
        segmentStarts.add(j);
    }

    @Override
    public FieldMatchMetrics clone() {
        try {
            FieldMatchMetrics clone = (FieldMatchMetrics)super.clone();
            clone.segmentStarts = new ArrayList<>(segmentStarts);
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error",e);
        }
    }

    @Override
    public String toString() {
        return "Metrics: [match: " + getMatch() + "]";
    }

    public String toStringDump() {
        try {
            StringBuilder b = new StringBuilder();
            for (Method m : this.getClass().getDeclaredMethods()) {
                if ( ! m.getName().startsWith("get")) continue;
                if (m.getReturnType() != Integer.TYPE && m.getReturnType() != Float.TYPE) continue;
                if ( m.getParameterTypes().length != 0 ) continue;

                Object value = m.invoke(this, new Object[0]);
                b.append(m.getName().substring(3, 4).toLowerCase() + m.getName().substring(4) + ": " + value + "\n");
            }
            return b.toString();
        }
        catch (Exception e) {
            throw new RuntimeException("Programming error", e);
        }
    }

    /** Returns the trace of this computation. This is empty (never null) if tracing is off */
    public Trace trace() { return trace; }

}
