// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Holds the settings for the matching feature.
 *
 * @author baldersheim
 */
public class Matching implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String TERMWISELIMIT = "termwiseLimit";
    public static final String NUMTHREADSPERSEARCH = "numThreadsPerSearch";
    public static final String NUMSEARCHPARTITIIONS = "numSearchPartitions";
    public static final String MINHITSPERTHREAD = "minHitsPerThread";
    public static final String POST_FILTER_THRESHOLD = "postFilterThreshold";
    public static final String APPROXIMATE_THRESHOLD = "approximateThreshold";

    static {
        argumentType =new QueryProfileType(Ranking.MATCHING);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(TERMWISELIMIT, "double"));
        argumentType.addField(new FieldDescription(NUMTHREADSPERSEARCH, "integer"));
        argumentType.addField(new FieldDescription(NUMSEARCHPARTITIIONS, "integer"));
        argumentType.addField(new FieldDescription(MINHITSPERTHREAD, "integer"));
        argumentType.addField(new FieldDescription(POST_FILTER_THRESHOLD, "double"));
        argumentType.addField(new FieldDescription(APPROXIMATE_THRESHOLD, "double"));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    public Double termwiseLimit = null;
    private Integer numThreadsPerSearch = null;
    private Integer numSearchPartitions = null;
    private Integer minHitsPerThread = null;
    private Double postFilterThreshold = null;
    private Double approximateThreshold = null;

    public Double getTermwiseLimit() { return termwiseLimit; }
    public Integer getNumThreadsPerSearch() { return numThreadsPerSearch; }
    public Integer getNumSearchPartitions() { return numSearchPartitions; }
    public Integer getMinHitsPerThread() { return minHitsPerThread; }
    public Double getPostFilterThreshold() { return postFilterThreshold; }
    public Double getApproximateThreshold() { return approximateThreshold; }

    public void setTermwiselimit(double value) {
        if ((value < 0.0) || (value > 1.0)) {
            throw new IllegalInputException("termwiselimit must be in the range [0.0, 1.0]. It is " + value);
        }
        termwiseLimit = value;
    }
    public void setNumThreadsPerSearch(int value) {
        numThreadsPerSearch = value;
    }
    public void setNumSearchPartitions(int value) {
        numSearchPartitions = value;
    }
    public void setMinHitsPerThread(int value) {
        minHitsPerThread = value;
    }
    public void setPostFilterThreshold(double threshold) {
        postFilterThreshold = threshold;
    }
    public void setApproximateThreshold(double threshold) {
        approximateThreshold = threshold;
    }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {

        if (termwiseLimit != null) {
            rankProperties.put("vespa.matching.termwise_limit", String.valueOf(termwiseLimit));
        }
        if (numThreadsPerSearch != null) {
            rankProperties.put("vespa.matching.numthreadspersearch", String.valueOf(numThreadsPerSearch));
        }
        if (numSearchPartitions != null) {
            rankProperties.put("vespa.matching.numsearchpartitions", String.valueOf(numSearchPartitions));
        }
        if (minHitsPerThread != null) {
            rankProperties.put("vespa.matching.minhitsperthread", String.valueOf(minHitsPerThread));
        }
        if (postFilterThreshold != null) {
            rankProperties.put("vespa.matching.global_filter.upper_limit", String.valueOf(postFilterThreshold));
        }
        if (approximateThreshold != null) {
            rankProperties.put("vespa.matching.global_filter.lower_limit", String.valueOf(approximateThreshold));
        }
    }

    @Override
    public Matching clone() {
        try {
            return (Matching) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Matching matching = (Matching) o;
        return Objects.equals(termwiseLimit, matching.termwiseLimit) &&
                Objects.equals(numThreadsPerSearch, matching.numThreadsPerSearch) &&
                Objects.equals(numSearchPartitions, matching.numSearchPartitions) &&
                Objects.equals(minHitsPerThread, matching.minHitsPerThread) &&
                Objects.equals(postFilterThreshold, matching.postFilterThreshold) &&
                Objects.equals(approximateThreshold, matching.approximateThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(termwiseLimit, numThreadsPerSearch, numSearchPartitions, minHitsPerThread, postFilterThreshold, approximateThreshold);
    }
}

