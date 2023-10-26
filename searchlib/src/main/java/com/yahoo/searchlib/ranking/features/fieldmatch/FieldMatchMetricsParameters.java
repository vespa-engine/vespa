// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

/**
 * The parameters to a string match metric calculator.
 * Mutable until frozen.
 *
 * @author  bratseth
 */
public final class FieldMatchMetricsParameters {

    private boolean frozen=false;

    private int proximityLimit=10;

    private int maxAlternativeSegmentations = 10000;

    private int maxOccurrences=100;

    private float proximityCompletenessImportance =0.9f;

    private float relatednessImportance =0.9f;

    private float earlinessImportance =0.05f;

    private float segmentProximityImportance =0.05f;

    private float occurrenceImportance =0.05f;

    private float fieldCompletenessImportance =0.05f;

    private float[] proximityTable= new float[] { 0.01f, 0.02f, 0.03f, 0.04f, 0.06f, 0.08f, 0.12f, 0.17f, 0.24f, 0.33f, 1,
                                                  0.71f, 0.50f, 0.35f, 0.25f, 0.18f, 0.13f, 0.09f, 0.06f, 0.04f, 0.03f };

    /* Calculation of the table above:
    static {
        System.out.println("Right order");
        for (float i=0; i<=10; i++)
            System.out.println(1/Math.pow(2,i/2));

        System.out.println("Reverse order");
        for (float i=0; i<=10; i++)
            System.out.println(1/Math.pow(2,i/2)/3);
    }
    */

    private static FieldMatchMetricsParameters defaultParameters;

    static {
        defaultParameters=new FieldMatchMetricsParameters();
        defaultParameters.freeze();
    }

    /** Returns the frozen default parameters */
    public static FieldMatchMetricsParameters defaultParameters() {
        return defaultParameters;
    }

    /** Creates an unfrozen marcg metrics object initialized to the default values */
    public FieldMatchMetricsParameters() { }

    /** Sets the maximum allowed gap within a segment. Default: 10 */
    public void setProximityLimit(int proximityLimit) {
        ensureNotFrozen();
        this.proximityLimit=proximityLimit;
    }

    /** Returns the maximum allowed gap within a segment. Default: 10 */
    public int getProximityLimit() { return proximityLimit; }

    /**
     * Sets the proximity table deciding the importance of separations of various distances,
     * The table must have size proximityLimit*2+1, where the first half is for reverse direction
     * distances. The table must only contain values between 0 and 1, where 1 is "perfect" and 0 is "worst".
     */
    public void setProximityTable(float[] proximityTable) {
        ensureNotFrozen();
        this.proximityTable=proximityTable;
    }

    /**
     * Returns the current proxmity table.
     * The default table is calculated by
     * <code>1/2^(n/2)</code> on the right order side, and
     * <code>1/2^(n/2) /3</code> on the reverse order side
     * where n is the distance between the tokens.
     */
    public float[] getProximityTable() { return proximityTable; }

    /** Returns the proximity table value at an index */
    public float getProximity(int index) { return proximityTable[index]; }

    /**
     * Returns the maximal number of <i>alternative</i> segmentations allowed in addition to the first one found.
     * Default is 10000. This will prefer to not consider iterations on segments that are far out in the field,
     * and which starts late in the query.
     */
    public int getMaxAlternativeSegmentations() { return maxAlternativeSegmentations; }

    public void setMaxAlternativeSegmentations(int maxAlternativeSegmentations) {
        ensureNotFrozen();
        this.maxAlternativeSegmentations = maxAlternativeSegmentations;
    }

    /**
     * Returns the number of occurrences the number of occurrences of each word is normalized against.
     * This should be set as the number above which additional occurrences of the term has no real significance.
     * The default is 100.
     */
    public int getMaxOccurrences() { return maxOccurrences; }

    public void setMaxOccurrences(int maxOccurrences) { this.maxOccurrences=maxOccurrences; }

    /**
     * Returns a number between 0 and 1 which determines the importancy of field completeness in relation to
     * query completeness in the <code>match</code> and <code>completeness</code> metrics. Default is 0.05
     */
    public float getFieldCompletenessImportance() { return fieldCompletenessImportance; }

    public void setFieldCompletenessImportance(float fieldCompletenessImportance) {
        ensureNotFrozen();
        this.fieldCompletenessImportance = fieldCompletenessImportance;
    }

    /**
     * Returns the importance of the match having high proximity and being complete, relative to segmentProximityImportance,
     * occurrenceImportance and earlinessImportance in the <code>match</code> metric. Default: 0.9
     */
    public float getProximityCompletenessImportance() { return proximityCompletenessImportance; }

    public void setProximityCompletenessImportance(float proximityCompletenessImportance) {
        ensureNotFrozen();
        this.proximityCompletenessImportance = proximityCompletenessImportance;
    }

    /**
     * Returns the importance of the match occuring early in the query, relative to segmentProximityImportance,
     * occurrenceImportance and proximityCompletenessImportance in the <code>match</code> metric. Default: 0.05
     */
    public float getEarlinessImportance() { return earlinessImportance; }

    public void setEarlinessImportance(float earlinessImportance) {
        ensureNotFrozen();
        this.earlinessImportance = earlinessImportance;
    }

    /**
     * Returns the importance of multiple segments being close to each other, relative to earlinessImportance,
     * occurrenceImportance and proximityCompletenessImportance in the <code>match</code> metric. Default: 0.05
     */
    public float getSegmentProximityImportance() { return segmentProximityImportance; }

    public void setSegmentProximityImportance(float segmentProximityImportance) {
        ensureNotFrozen();
        this.segmentProximityImportance = segmentProximityImportance;
    }

    /**
     * Returns the importance of having many occurrences of the query terms, relative to earlinessImportance,
     * segmentProximityImportance and proximityCompletenessImportance in the <code>match</code> metric. Default: 0.05
     */
    public float getOccurrenceImportance() { return occurrenceImportance; }

    public void setOccurrenceImportance(float occurrenceImportance) {
        ensureNotFrozen();
        this.occurrenceImportance = occurrenceImportance;
    }

    /** Returns the normalized importance of relatedness used in the <code>match</code> metric. Default: 0.9 */
    public float getRelatednessImportance() { return relatednessImportance; }

    public void setRelatednessImportance(float relatednessImportance) {
        ensureNotFrozen();
        this.relatednessImportance = relatednessImportance;
    }


    /** Throws IllegalStateException if this is frozen. Does nothing otherwise */
    private void ensureNotFrozen() {
        if (frozen)
            throw new IllegalStateException(this + " is frozen");
    }

    /**
     * Freezes this object. All changes after this point will cause an IllegalStateException.
     * This must be frozen before being handed to a calculator.
     *
     * @throws IllegalStateException if this parameter object is inconsistent. In this case, this is not frozen.
     */
    public void freeze() {
        if (proximityTable.length!=proximityLimit*2+1)
            throw new IllegalStateException("Proximity table length is " + proximityTable.length + ". Must be " +
                                            (proximityLimit*2+1) +
                                            " (proximityLimit*2+1), because the proximity limit is " + proximityLimit);
        frozen=true;
    }

}
