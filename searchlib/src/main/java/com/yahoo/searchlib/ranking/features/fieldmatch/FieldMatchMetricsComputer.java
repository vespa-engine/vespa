// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Calculates a set of metrics capturing information about the degree of agreement between a query
 * and a field string. This algorithm attempts to capture the property of text that very close tokens
 * are usually part of the same semantic structure, while tokens farther apart are much more loosely related.
 * The algorithm will locate alternative such regions containing multiple query tokens (segments), do a more
 * detailed analysis of these segments and choose the ones producing the best overall set of match metrics
 * (subject to certain resource constraints).</p>
 *
 * <p>Such segments are found by looking at query terms in sequence from
 * left to right and finding matches in the field. All alternative segment start points are explored, and the
 * segmentation achieving the best overall string match metric score is preferred. Dynamic programming
 * is used to avoid redoing work on segmentations.</p>
 *
 * <p>When a segment start point is found, subsequent tokens from the query are searched in the field
 * from this starting point in "semantic order". This search order can be defined independently of the
 * algorithm. The current order searches <i>proximityLimit</i> tokens ahead first, then the same distance backwards
 * (so if you need to go two steps backwards in the field from the segment starting point, the real distance is -2,
 * but the "semantic distance" is proximityLimit+2).</p>
 *
 * <p>The actual metrics are calculated during execution of this algorithm by the {@link FieldMatchMetrics} class,
 * by receiving events emitted from the algorithm. Any set of metrics derivable from these events are computable using
 * this algorithm.</p>
 *
 * <p>Terminology:
 * <ul>
 * <li><b>Sequence</b> - A set of adjacent matched tokens in the field
 * <li><b>Segment</b> - A field area containing matches to a continuous section of the query
 * <li><b>Gap</b> - A chunk of adjacent tokens <i>inside a segment</i> separating two matched characters
 * <li><b>Semantic distance</b> - A non-continuous distance between tokens in j, where the non-continuousness is
 * mean to capture the semantic similarity between the query and those tokens.
 * </ul>
 *
 * <p>Notation: A position index in the query is denoted <code>i</code>. A position index in the field is
 * denoted <code>j</code>.</p>
 *
 * <p>This class is not multithread safe, but is reusable across queries for a single thread.</p>
 *
 * @author  bratseth
 */
public final class FieldMatchMetricsComputer {

    private Query query;

    private Field field;

    private final FieldMatchMetricsParameters parameters;

    /** The metrics of the currently explored segmentation */
    private FieldMatchMetrics metrics;

    /**
     * Known segment starting points. The array is 0..i, one element per starting point query item i,
     * and a last element representing the entire query.
     */
    private List<SegmentStartPoint> segmentStartPoints=new ArrayList<>();

    /** True to collect trace */
    private boolean collectTrace;

    private int alternativeSegmentationsTried=0;

    /** Creates a feature computer using default settings */
    public FieldMatchMetricsComputer() {
        this(FieldMatchMetricsParameters.defaultParameters());
    }

    /**
     * Creates a feature computer with the given parameters.
     * The parameters are frozen if they were not already, this may cause
     * validation exceptions to be thrown from this.
     */
    public FieldMatchMetricsComputer(FieldMatchMetricsParameters parameters) {
        this.parameters = parameters;
    }

    /** Computes the string match metrics from a query and field string. */
    public FieldMatchMetrics compute(String queryString,String fieldString) {
        return compute(new Query(queryString), fieldString);
    }

    /** Computes the string match metrics from a query and field string. */
    public FieldMatchMetrics compute(Query query, String fieldString) {
        return compute(query,fieldString,false);
    }

    /**
     * Computes the string match metrics from a query and field string.
     *
     * @param query the query to compute over
     * @param fieldString the field value to compute over - tokenized by splitting on space
     * @param collectTrace true to accumulate trace information in the trace returned with the metrics
     */
    public FieldMatchMetrics compute(Query query, String fieldString, boolean collectTrace) {
        return compute(query, new Field(fieldString), collectTrace);
    }

    /**
     * Computes the string match metrics from a query and field.
     *
     * @param query the query to compute over
     * @param field the field value to compute over
     * @param collectTrace true to accumulate trace information in the trace returned with the metrics
     */
    public FieldMatchMetrics compute(Query query, Field field, boolean collectTrace) {
        // 1. Reset state
        this.collectTrace = collectTrace;
        this.query = query;
        this.field = field;
        segmentStartPoints.clear();
        for (int i = 0; i <= query.getTerms().length; i++)
            segmentStartPoints.add(null);
        alternativeSegmentationsTried = 0;
        metrics = new FieldMatchMetrics(this);

        // 2. Compute
        exploreSegments();
        return metrics;
    }

    /** Finds segment candidates and explores them until we have the best segmentation history of the entire query */
    private void exploreSegments() {
        if (collectTrace)
            metrics.trace().add("Calculating matches for\n    " + query + "\n    " + field + "\n");

        // Create an initial start point
        SegmentStartPoint segmentStartPoint=new SegmentStartPoint(metrics,this);
        segmentStartPoints.set(0,segmentStartPoint);

        // Explore segmentations
        while (segmentStartPoint!=null) {
            metrics = segmentStartPoint.getMetrics().clone();
            if (collectTrace)
                metrics.trace().add("\nLooking for segment from " + segmentStartPoint + "..." + "\n");
            boolean found=findAlternativeSegmentFrom(segmentStartPoint);
            if (collectTrace)
                metrics.trace().add(found ? "...found segment: " + metrics.getSegmentStarts() + " score: " +
                        metrics.getSegmentationScore() : "...no complete and improved segment existed" + "\n");
            if (!found)
                segmentStartPoint.setOpen(false);
            segmentStartPoint=findOpenSegment(segmentStartPoint.getI());
        }

        metrics = findLastStartPoint().getMetrics(); // these metrics are the final set
        setOccurrenceCounts(metrics);
        metrics.onComplete();
        metrics.setComplete(true);
    }

    /**
     * Find correspondences from a segment starting point
     *
     * @return true if a segment was found, false if none could be found
     */
    private boolean findAlternativeSegmentFrom(SegmentStartPoint segmentStartPoint) {
        // i: index into the query
        // j: index into the field
        int semanticDistanceExplored=segmentStartPoint.getSemanticDistanceExplored();
        int previousI=-1;
        int previousJ=segmentStartPoint.getPreviousJ();
        boolean hasOpenSequence=false;
        boolean isFirst=true;

        for (int i=segmentStartPoint.getStartI(); i<query.getTerms().length; i++) {
            int semanticDistance=findClosestInFieldBySemanticDistance(i,previousJ,semanticDistanceExplored);
            int j=semanticDistanceToFieldIndex(semanticDistance,previousJ);

            if (j==-1 && semanticDistanceExplored>0 && isFirst) {
                return false; // Segment explored before, and no more matches found
            }

            if ( hasOpenSequence && ( j==-1 || j!=previousJ+1 ) ) {
                metrics.onSequenceEnd(previousJ);
                hasOpenSequence=false;
            }

            if (isFirst) {
                if (j!=-1) {
                    segmentStart(i,j,isFirst ? -1 : previousJ);
                    segmentStartPoint.exploredTo(j);
                    isFirst=false;
                }
                else {
                    segmentStartPoint.incrementStartI(); // Remember that there are no matches for this i
                }
            }
            else {
                if (Math.abs(j-previousJ) >= parameters.getProximityLimit()) {
                    segmentEnd(i-1,previousJ);
                    return true;
                }
                else if (j!=-1) {
                    inSegment(i,j,previousJ,previousI);
                }
            }

            if (j!=-1)
                metrics.onMatch(i,j);

            if (j!=-1 && !hasOpenSequence) {
                metrics.onSequenceStart(j);
                hasOpenSequence=true;
            }

            if (j!=-1)
                semanticDistanceExplored=1; // Skip the current match when looking for the next
            else
                semanticDistanceExplored=0;

            if (j>=0) {
                previousI=i;
                previousJ=j;
            }
        }

        if (hasOpenSequence)
            metrics.onSequenceEnd(previousJ);

        if (!isFirst) {
            segmentEnd(query.getTerms().length-1,previousJ);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Implements the preferred search order for finding a match to a query item - first
     * looking close in the right order, then close in the reverse order, then far in the right order
     * and lastly far in the reverse order.
     *
     * @param startSemanticDistance is the semantic distance we must be larger than or equal to
     * @return the semantic distance of the next mathing j larger than startSemanticDistance, or -1 if
     *         there are no matches larger than startSemanticDistance
     */
    private int findClosestInFieldBySemanticDistance(int i,int previousJ,int startSemanticDistance) {
        String term=query.getTerms()[i].getTerm();
        for (int distance=startSemanticDistance; distance<field.terms().size(); distance++) {
            int j=semanticDistanceToFieldIndex(distance,previousJ);
            if (term.equals(field.terms().get(j).value()))
                return distance;
        }
        return -1;
    }

    /**
     * Returns the field index (j) from a starting point zeroJ and the distance form zeroJ in the
     * semantic distance space
     *
     * @return the field index, or -1 (undefined) if the semanticDistance is -1
     */
    int semanticDistanceToFieldIndex(int semanticDistance,int zeroJ) {
        if (semanticDistance == -1) return -1;
        int firstSegmentLength = Math.min(parameters.getProximityLimit(),field.terms().size() - zeroJ);
        int secondSegmentLength = Math.min(parameters.getProximityLimit(), zeroJ);
        if (semanticDistance < firstSegmentLength)
            return zeroJ + semanticDistance;
        else if (semanticDistance < firstSegmentLength+secondSegmentLength)
            return zeroJ - semanticDistance - 1 + firstSegmentLength;
        else if (semanticDistance < field.terms().size() - zeroJ+secondSegmentLength)
            return zeroJ + semanticDistance - secondSegmentLength;
        else
            return field.terms().size() - semanticDistance - 1;
    }

    /**
     * Returns the semantic distance from a starting point zeroJ to a field index j
     *
     * @return the semantic distance, or -1 (undefined) if j is -1
     */
    int fieldIndexToSemanticDistance(int j,int zeroJ) {
        if (j == -1) return -1;
        int firstSegmentLength = Math.min(parameters.getProximityLimit(), field.terms().size() - zeroJ);
        int secondSegmentLength = Math.min(parameters.getProximityLimit(), zeroJ);
        if (j >= zeroJ) {
            if ( (j - zeroJ) < firstSegmentLength )
                return j - zeroJ; // 0..limit
            else
                return j - zeroJ+secondSegmentLength; // limit*2..field.length-zeroJ
        }
        else {
            if ( (zeroJ - j - 1) < secondSegmentLength )
                return zeroJ - j + firstSegmentLength-1; // limit..limit*2
            else
                return (zeroJ - j - 1) + field.terms().size() - zeroJ; // field.length-zeroJ..
        }

    }

    private void inSegment(int i, int j, int previousJ, int previousI) {
        metrics.onPair(i, j, previousJ);
        if (j==previousJ+1 && i==previousI+1) {
            metrics.onInSequence(i, j, previousJ);
        }
        else {
            metrics.onInSegmentGap(i, j, previousJ);
            if (collectTrace)
                metrics.trace().add("      in segment gap: " + i + "->" + j + " (" + query.getTerms()[i] + ")" + "\n");
        }
    }

    /** Returns whether this segment was accepted as a starting point */
    private boolean segmentStart(int i,int j,int previousJ) {
        metrics.onNewSegment(i, j, previousJ);

        if (previousJ>=0)
            metrics.onPair(i,j,previousJ);

        if (collectTrace)
            metrics.trace().add("    new segment at:   " + i + "->" + j + " (" + query.getTerms()[i] + ")" + "\n");
        return true;
    }

    /**
     * Registers an end of a segment
     *
     * @param i the i at which this segment ends
     * @param j the j at which this segment ends
     */
    private void segmentEnd(int i,int j) {
        if (collectTrace)
            metrics.trace().add("    segment ended at: " + i + "->" + j + " (" + query.getTerms()[i] + ")" + "\n");
        SegmentStartPoint startOfNext=segmentStartPoints.get(i + 1);
        if (startOfNext==null)
            segmentStartPoints.set(i+1,new SegmentStartPoint(i+1,j, metrics,this));
        else
            startOfNext.offerHistory(j, metrics, collectTrace);
    }

    /** Returns the next open segment to explore, or null if no more segments exists or should be explored */
    private SegmentStartPoint findOpenSegment(int startI) {
        for (int i=startI; i<segmentStartPoints.size(); i++) {
            SegmentStartPoint startPoint=segmentStartPoints.get(i);
            if (startPoint==null || !startPoint.isOpen()) continue;

            if (startPoint.getSemanticDistanceExplored()==0) return startPoint; // First attempt

            if (alternativeSegmentationsTried>=parameters.getMaxAlternativeSegmentations()) continue;
            alternativeSegmentationsTried++;
            return startPoint;
        }

        return null;
    }

    private SegmentStartPoint findLastStartPoint() {
        for (int i=segmentStartPoints.size()-1; i>=0; i--) {
            SegmentStartPoint startPoint=segmentStartPoints.get(i);
            if (startPoint!=null)
                return startPoint;
        }
        return null; // Impossible
    }

    /** Counts all occurrences of terms of the query in the field and set those metrics */
    private void setOccurrenceCounts(FieldMatchMetrics metrics) {
        Set<QueryTerm> uniqueQueryTerms=new HashSet<>();
        for (QueryTerm queryTerm : query.getTerms())
            uniqueQueryTerms.add(queryTerm);

        List<Float> weightedOccurrences=new ArrayList<Float>();
        List<Float> significantOccurrences=new ArrayList<Float>();

        int divider = Math.min(field.terms().size(),parameters.getMaxOccurrences()*uniqueQueryTerms.size());
        int maxOccurence = Math.min(field.terms().size(),parameters.getMaxOccurrences());

        float occurrence=0;
        float absoluteOccurrence=0;
        float weightedAbsoluteOccurrence=0;
        int totalWeight=0;
        float totalWeightedOccurrences=0;
        float totalSignificantOccurrences=0;

        for (QueryTerm queryTerm : uniqueQueryTerms) {
            int termOccurrences=0;
            for (Field.Term fieldTerm : field.terms()) {
                if (fieldTerm.value().equals(queryTerm.getTerm()))
                    termOccurrences++;
                if (termOccurrences == parameters.getMaxOccurrences()) break;
            }
            occurrence+=(float)termOccurrences/divider;

            absoluteOccurrence+=(float)termOccurrences/(parameters.getMaxOccurrences()*uniqueQueryTerms.size());

            weightedAbsoluteOccurrence+=(float)termOccurrences*queryTerm.getWeight()/parameters.getMaxOccurrences();
            totalWeight+=queryTerm.getWeight();

            totalWeightedOccurrences+=(float)maxOccurence*queryTerm.getWeight()/divider;
            weightedOccurrences.add((float)termOccurrences*queryTerm.getWeight()/divider);

            totalSignificantOccurrences+=(float)maxOccurence*queryTerm.getSignificance()/divider;
            significantOccurrences.add((float)termOccurrences*queryTerm.getSignificance()/divider);
        }

        float weightedOccurrenceSum=0;
        for (float weightedOccurence : weightedOccurrences)
            weightedOccurrenceSum+=weightedOccurence/totalWeightedOccurrences;

        float significantOccurrenceSum=0;
        for (float significantOccurence : significantOccurrences)
            significantOccurrenceSum+=significantOccurence/totalSignificantOccurrences;

        if (totalWeight>0)
            weightedAbsoluteOccurrence=weightedAbsoluteOccurrence/totalWeight;

        metrics.setOccurrence(occurrence);
        metrics.setAbsoluteOccurrence(absoluteOccurrence);
        metrics.setWeightedOccurrence(weightedOccurrenceSum);
        metrics.setWeightedAbsoluteOccurrence(weightedAbsoluteOccurrence);
        metrics.setSignificantOccurrence(significantOccurrenceSum);
    }

    /** Returns the parameter settings of this */
    public FieldMatchMetricsParameters getParameters() { return parameters; }

    Query getQuery() { return query; }

    Field getField() { return field; }

    @Override
    public String toString() {
        return query + "\n" + field + "\n" + metrics + "\n";
    }

}
