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

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    static {
        argumentType = new QueryProfileType(Ranking.SECOND_PHASE);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(Ranking.RANKSCOREDROPLIMIT, FieldType.doubleType));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Double rankScoreDropLimit = null;

    /** Sets the second phase rank-score-drop-limit that will be used, or null if not set */
    public void setRankScoreDropLimit(double rankScoreDropLimit) { this.rankScoreDropLimit = rankScoreDropLimit; }

    /** Returns the second phase rank-score-drop-limit that will be used, or null if not set */
    public Double getRankScoreDropLimit() { return rankScoreDropLimit; }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {
        if (rankScoreDropLimit == null) {
            return;
        }
        rankProperties.put("vespa.hitcollector.secondphase.rankscoredroplimit", String.valueOf(rankScoreDropLimit));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.rankScoreDropLimit);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof SecondPhase other) {
            if ( ! Objects.equals(this.rankScoreDropLimit, other.rankScoreDropLimit)) return false;
            return true;
        }
        return false;
    }

    @Override
    public SecondPhase clone() {
        try {
            SecondPhase clone = (SecondPhase)super.clone();
            clone.rankScoreDropLimit = this.rankScoreDropLimit;
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

}
