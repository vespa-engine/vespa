// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Query properties exposing WeakAnd-specific settings.
 *
 * @author vekterli
 */
public class WeakAnd implements Cloneable {

    private static final QueryProfileType argumentType;

    public static final String STOP_WORD_LIMIT = "stopwordLimit";
    public static final String ADJUST_TARGET = "adjustTarget";

    static {
        argumentType = new QueryProfileType(Matching.WEAKAND);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(STOP_WORD_LIMIT, "double"));
        argumentType.addField(new FieldDescription(ADJUST_TARGET, "double"));
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    private Double stopWordLimit = null;
    private Double adjustTarget = null;

    public Double getStopWordLimit() { return stopWordLimit; }
    public Double getAdjustTarget() { return adjustTarget; }

    private static void validateRange(String field, double v, double lboundIncl, double uboundIncl) {
        if (v < lboundIncl || v > uboundIncl) {
            throw new IllegalArgumentException("%s must be in the range [%.1f, %.1f]. It is %.1f".formatted(field, lboundIncl, uboundIncl, v));
        }
    }

    public void setStopWordLimit(double limit) {
        validateRange(STOP_WORD_LIMIT, limit, 0.0, 1.0);
        stopWordLimit = limit;
    }
    public void setAdjustTarget(double target) {
        validateRange(ADJUST_TARGET, target, 0.0, 1.0);
        adjustTarget = target;
    }

    /** Internal operation - DO NOT USE DIRECTLY */
    public void prepare(RankProperties rankProperties) {
        if (stopWordLimit != null) {
            rankProperties.put("vespa.matching.weakand.stop_word_drop_limit", String.valueOf(stopWordLimit));
        }
        if (adjustTarget != null) {
            rankProperties.put("vespa.matching.weakand.stop_word_adjust_limit", String.valueOf(adjustTarget));
        }
    }

    @Override
    public WeakAnd clone() {
        try {
            return (WeakAnd)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        WeakAnd weakAnd = (WeakAnd) o;
        return Objects.equals(stopWordLimit, weakAnd.stopWordLimit) &&
                Objects.equals(adjustTarget, weakAnd.adjustTarget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopWordLimit, adjustTarget);
    }
}
