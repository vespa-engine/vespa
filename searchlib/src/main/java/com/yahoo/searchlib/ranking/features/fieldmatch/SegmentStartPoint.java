// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

/**
 * <p>Information on segment start points stored temporarily during string match metric calculation.</p>
 *
 * <p>Given that we want to start a segment at i, this holdes the best known metrics up to i
 * and the end of the previous segment. In addition it holds information on how far we have tried
 * to look for alternative segments from this starting point (skipI and previousJ).</p>
 *
 * @author  bratseth
 */
final class SegmentStartPoint {

    private FieldMatchMetricsComputer owner;

    /** The i for which this is the possible segment starting points */
    private int i;

    private int skipI;

    /** The best known metrics up to this starting point */
    private FieldMatchMetrics metrics;

    /** The j ending the previous segmentation producing those best metrics */
    private int previousJ;

    /** The semantic distance from the current previousJ which is already explored */
    private int semanticDistanceExplored=0;

    /** There are possibly more j's to try at this starting point */
    boolean open=true;

    /** Creates a segment start point for the first segment */
    public SegmentStartPoint(FieldMatchMetrics metrics, FieldMatchMetricsComputer owner) {
        this.i=0;
        this.previousJ=0;
        this.metrics=metrics;
        this.owner=owner;
        this.semanticDistanceExplored=0;
    }

    /** Creates a segment start point for any i position where the j is not known */
    public SegmentStartPoint(int i,int previousJ,FieldMatchMetrics metrics, FieldMatchMetricsComputer owner) {
        this.i=i;
        this.previousJ=previousJ;
        this.metrics=metrics;
        this.owner=owner;
        this.semanticDistanceExplored=0;
    }

    /** Creates a segment start point for any position, where the j of the start point is known */
    public SegmentStartPoint(int i,int j,int previousJ,FieldMatchMetrics metrics, FieldMatchMetricsComputer owner) {
        this.i=i;
        this.previousJ=previousJ;
        this.metrics=metrics;
        this.owner=owner;
        this.semanticDistanceExplored=owner.fieldIndexToSemanticDistance(j,previousJ)+1;
    }

    /** Returns the current best metrics for this starting point */
    public FieldMatchMetrics getMetrics() { return metrics; }

    /**
     * Stores that we have explored to a certain j from the current previousJ.
     */
    public void exploredTo(int j) {
        semanticDistanceExplored=owner.fieldIndexToSemanticDistance(j,previousJ)+1;
    }

    /**
     * Offers an alternative history leading up to this point, which is accepted and stored if it is
     * better than the current history
     */
    public void offerHistory(int offeredPreviousJ,FieldMatchMetrics offeredMetrics,boolean collectTrace) {
        if (offeredMetrics.getSegmentationScore()<=metrics.getSegmentationScore()) {
            if (collectTrace)
                offeredMetrics.trace().add("    rejected offered history [match: " + offeredMetrics.getSegmentationScore() +
                                           " ending at:" + previousJ + "] at " + this + "\n");
            return; // Reject
        }

        /*
        if (previousJ!=offeredPreviousJ) { // Starting over like this achieves higher correctness if
            semanticDistanceExplored=0;    // the match metric is dependent on relative distance between segments
            open=true;                     // but is more expensive
        }
        */

        if (collectTrace)
            offeredMetrics.trace().add("    accepted offered history [match: " + offeredMetrics.getSegmentationScore() +
                                       " ending at:" + previousJ + "] at " + this + "\n");

        previousJ=offeredPreviousJ;
        metrics=offeredMetrics;
    }

    /**
     * Returns whether there are possibly still unexplored j's for this i
     */
    public boolean isOpen() { return open; }

    public void setOpen(boolean open) { this.open=open; }

    /** Returns the i for which this is the possible segment starting points */
    public int getI() { return i; }

    /**
     * Returns the j ending the previous segmentation producing those best metrics,
     */
    public int getPreviousJ() { return previousJ; }

    /**
     * Returns the semantic distance from the previous j which is explored so far, exclusive
     * (meaning, if the value is 0, 0 is <i>not</i> explored yet)
     */
    public int getSemanticDistanceExplored() { return semanticDistanceExplored; }

    public void setSemanticDistanceExplored(int distance) { this.semanticDistanceExplored=distance; }

    /**
     * Returns the position startI we should start at from this start point i.
     * startI==i except when there are i's from this starting point which are not found anywhere in
     * the field. In that case, startI==i+the number of terms following i which are known not to be present
     */
    public int getStartI() {
        return i+skipI;
    }

    /**
     * Increments the startI by one because we have discovered that the term at the current startI is not
     * present in the field
     */
    public void incrementStartI() { skipI++; }

    public String toString() {
        if (i==owner.getQuery().getTerms().length)
            return "last segment: Complete match: " + metrics.getMatch() + " previous j: " + previousJ +
                    " (" + (open ? "open" : "closed") + ")";
        return "segment at " + i + " (" + owner.getQuery().getTerms()[i] + "): Match up to here: " + metrics.getMatch() + " previous j: " +
                previousJ +  " explored to: " + semanticDistanceExplored +
                " (" + (open ? "open" : "closed") + ")";
    }

}
