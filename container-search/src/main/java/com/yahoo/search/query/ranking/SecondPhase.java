// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * The second-phase ranking settings of this query.
 *
 * @author toregge
 */
public class SecondPhase implements Cloneable {

    /** For internal use only. */
    public static final String rerankCountProperty      = "vespa.hitcollector.heapsize";

    /** For internal use only. */
    public static final String totalRerankCountProperty = "vespa.hitcollector.totalHeapsize";

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String SECOND_PHASE = "secondPhase";

    static {
        argumentType = new QueryProfileType(SECOND_PHASE);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(Ranking.RANKSCOREDROPLIMIT, FieldType.doubleType));
        argumentType.addField(new FieldDescription(Ranking.RERANKCOUNT, FieldType.integerType));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Integer rerankCount = null;
    private Integer totalRerankCount = null;
    private Double rankScoreDropLimit = null;

    /**
     * Sets the number of hits for which the second-phase function will be evaluated on each node.
     * When set, this overrides the setting in the rank profile, and the totalRerankCount setting.
     */
    public void setRerankCount(int rerankCount) { this.rerankCount = rerankCount; }

    /**
     * Returns the number of hits for which the second-phase function will be evaluated per node.
     * When set, this overrides the setting in the rank profile, and the totalRerankCount setting.
     */
    public Integer getRerankCount() { return rerankCount; }

    /**
     * Sets the number of hits for which the second-phase function will be evaluated in total
     * across all nodes in the group.
     * When set, this overrides the setting in the rank profile.
     */
    public void setTotalRerankCount(int totalRerankCount) { this.totalRerankCount = totalRerankCount; }

    /**
     * Returns the number of hits for which the second-phase function will be evaluated in total
     * across all nodes in the group.
   . */
    public Integer getTotalRerankCount() { return totalRerankCount; }

    /** Sets the second phase rank-score-drop-limit that will be used, or null if not set. */
    public void setRankScoreDropLimit(double rankScoreDropLimit) { this.rankScoreDropLimit = rankScoreDropLimit; }

    /** Returns the second phase rank-score-drop-limit that will be used, or null if not set */
    public Double getRankScoreDropLimit() { return rankScoreDropLimit; }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {
        if (rankScoreDropLimit != null)
            rankProperties.put("vespa.hitcollector.secondphase.rankscoredroplimit", String.valueOf(rankScoreDropLimit));
    }

    @Override
    public int hashCode() {
        return Objects.hash(rerankCount, totalRerankCount, rankScoreDropLimit);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof SecondPhase other)) return false;
        if ( ! Objects.equals(this.rerankCount, other.rerankCount)) return false;
        if ( ! Objects.equals(this.totalRerankCount, other.totalRerankCount)) return false;
        if ( ! Objects.equals(this.rankScoreDropLimit, other.rankScoreDropLimit)) return false;
        return true;
    }

    @Override
    public SecondPhase clone() {
        try {
            return (SecondPhase)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

}
