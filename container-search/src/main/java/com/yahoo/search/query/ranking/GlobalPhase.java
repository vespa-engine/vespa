// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * The global-phase ranking settings of this query.
 *
 * @author arnej
 */
public class GlobalPhase implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    static {
        argumentType = new QueryProfileType(Ranking.GLOBAL_PHASE);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(Ranking.RERANKCOUNT, FieldType.integerType));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Integer rerankCount = null;

    /**
     * Sets the number of hits for which the global-phase function will be evaluated.
     * When set, this overrides the setting in the rank profile.
     */
    public void setRerankCount(int rerankCount) { this.rerankCount = rerankCount; }

    /** Returns the rerank-count that will be used, or null if not set */
    public Integer getRerankCount() { return rerankCount; }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.rerankCount);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof GlobalPhase other) {
            if ( ! Objects.equals(this.rerankCount, other.rerankCount)) return false;
            return true;
        }
        return false;
    }

    @Override
    public GlobalPhase clone() {
        try {
            GlobalPhase clone = (GlobalPhase)super.clone();
            clone.rerankCount = this.rerankCount;
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

}
