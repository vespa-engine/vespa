// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Locale;
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
    public static final String EXPLORATION_SLACK = "explorationSlack";
    public static final String APPROXIMATE_THRESHOLD = "approximateThreshold";
    public static final String FILTER_FIRST_THRESHOLD = "filterFirstThreshold";
    public static final String FILTER_FIRST_EXPLORATION = "filterFirstExploration";
    public static final String TARGET_HITS_MAX_ADJUSTMENT_FACTOR = "targetHitsMaxAdjustmentFactor";
    public static final String FILTER_THRESHOLD = "filterThreshold";
    public static final String WEAKAND = "weakand";

    static {
        argumentType = new QueryProfileType(Ranking.MATCHING);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(TERMWISELIMIT, "double"));
        argumentType.addField(new FieldDescription(NUMTHREADSPERSEARCH, "integer"));
        argumentType.addField(new FieldDescription(NUMSEARCHPARTITIIONS, "integer"));
        argumentType.addField(new FieldDescription(MINHITSPERTHREAD, "integer"));
        argumentType.addField(new FieldDescription(POST_FILTER_THRESHOLD, "double"));
        argumentType.addField(new FieldDescription(APPROXIMATE_THRESHOLD, "double"));
        argumentType.addField(new FieldDescription(FILTER_FIRST_THRESHOLD, "double"));
        argumentType.addField(new FieldDescription(FILTER_FIRST_EXPLORATION, "double"));
        argumentType.addField(new FieldDescription(EXPLORATION_SLACK, "double"));
        argumentType.addField(new FieldDescription(TARGET_HITS_MAX_ADJUSTMENT_FACTOR, "double"));
        argumentType.addField(new FieldDescription(FILTER_THRESHOLD, "double"));
        argumentType.addField(new FieldDescription(WEAKAND, new QueryProfileFieldType(WeakAnd.getArgumentType())));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    public Double termwiseLimit = null;
    private Integer numThreadsPerSearch = null;
    private Integer numSearchPartitions = null;
    private Integer minHitsPerThread = null;
    private Double postFilterThreshold = null;
    private Double approximateThreshold = null;
    private Double filterFirstThreshold = null;
    private Double filterFirstExploration = null;
    private Double explorationSlack = null;
    private Double targetHitsMaxAdjustmentFactor = null;
    private Double filterThreshold = null;
    private WeakAnd weakAnd = new WeakAnd();

    public Double getTermwiseLimit() { return termwiseLimit; }
    public Integer getNumThreadsPerSearch() { return numThreadsPerSearch; }
    public Integer getNumSearchPartitions() { return numSearchPartitions; }
    public Integer getMinHitsPerThread() { return minHitsPerThread; }
    public Double getPostFilterThreshold() { return postFilterThreshold; }
    public Double getApproximateThreshold() { return approximateThreshold; }
    public Double getFilterFirstThreshold() { return filterFirstThreshold; }
    public Double getFilterFirstExploration() { return filterFirstExploration; }
    public Double getExplorationSlack() { return explorationSlack; }
    public Double getTargetHitsMaxAdjustmentFactor() { return targetHitsMaxAdjustmentFactor; }
    public Double getFilterThreshold() { return filterThreshold; }
    public WeakAnd getWeakAnd() { return weakAnd; }

    private static void validateRange(String field, double v, double lboundIncl, double uboundIncl) {
        if (v < lboundIncl || v > uboundIncl) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s must be in the range [%.1f, %.1f]. It is %.1f",
                                                             field, lboundIncl, uboundIncl, v));

        }
    }

    public void setTermwiselimit(double value) {
        validateRange(TERMWISELIMIT, value, 0.0, 1.0);
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
    public void setFilterFirstThreshold(double threshold) {
        filterFirstThreshold = threshold;
    }
    public void setFilterFirstExploration(double threshold) {
        filterFirstExploration = threshold;
    }
    public void setExplorationSlack(double slack) {
        explorationSlack = slack;
    }
    public void setTargetHitsMaxAdjustmentFactor(double factor) {
        targetHitsMaxAdjustmentFactor = factor;
    }
    public void setFilterThreshold(double threshold) {
        validateRange(FILTER_THRESHOLD, threshold, 0.0, 1.0);
        filterThreshold = threshold;
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
        if (filterFirstThreshold != null) {
            rankProperties.put("vespa.matching.nns.filter_first_upper_limit", String.valueOf(filterFirstThreshold));
        }
        if (filterFirstThreshold != null) {
            rankProperties.put("vespa.matching.nns.filter_first_exploration", String.valueOf(filterFirstExploration));
        }
        if (explorationSlack != null) {
            rankProperties.put("vespa.matching.nns.exploration_slack", String.valueOf(explorationSlack));
        }
        if (targetHitsMaxAdjustmentFactor != null) {
            rankProperties.put("vespa.matching.nns.target_hits_max_adjustment_factor", String.valueOf(targetHitsMaxAdjustmentFactor));
        }
        if (filterThreshold != null) {
            rankProperties.put("vespa.matching.filter_threshold", String.valueOf(filterThreshold));
        }
        weakAnd.prepare(rankProperties);
    }

    @Override
    public Matching clone() {
        try {
            var clone =  (Matching) super.clone();
            clone.weakAnd = this.weakAnd.clone();
            return clone;
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
               Objects.equals(approximateThreshold, matching.approximateThreshold) &&
               Objects.equals(filterFirstThreshold, matching.filterFirstThreshold) &&
               Objects.equals(filterFirstExploration, matching.filterFirstExploration) &&
               Objects.equals(explorationSlack, matching.explorationSlack) &&
               Objects.equals(targetHitsMaxAdjustmentFactor, matching.targetHitsMaxAdjustmentFactor) &&
               Objects.equals(filterThreshold, matching.filterThreshold) &&
               Objects.equals(weakAnd, matching.weakAnd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(termwiseLimit, numThreadsPerSearch, numSearchPartitions, minHitsPerThread,
                            postFilterThreshold, approximateThreshold, filterFirstThreshold, filterFirstExploration,
                            explorationSlack, targetHitsMaxAdjustmentFactor, filterThreshold, weakAnd);
    }
}

