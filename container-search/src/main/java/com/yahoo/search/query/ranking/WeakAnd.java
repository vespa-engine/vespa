// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Locale;
import java.util.Objects;

/**
 * Query properties exposing WeakAnd-specific settings.
 *
 * @author vekterli
 */
public class WeakAnd implements Cloneable {

    private static final QueryProfileType argumentType;

    public static final String STOPWORD_LIMIT = "stopwordLimit";
    public static final String ADJUST_TARGET = "adjustTarget";
    public static final String ALLOW_DROP_ALL = "allowDropAll";

    static {
        argumentType = new QueryProfileType(Matching.WEAKAND);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(STOPWORD_LIMIT, "double"));
        argumentType.addField(new FieldDescription(ADJUST_TARGET, "double"));
        argumentType.addField(new FieldDescription(ALLOW_DROP_ALL, "boolean"));
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    private Double stopwordLimit = null;
    private Double adjustTarget = null;
    private Boolean allowDropAll = null;

    public Double getStopwordLimit() { return stopwordLimit; }
    public Double getAdjustTarget() { return adjustTarget; }
    public Boolean getAllowDropAll() { return allowDropAll; }

    private static void validateRange(String field, double v, double lboundIncl, double uboundIncl) {
        if (v < lboundIncl || v > uboundIncl) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s must be in the range [%.1f, %.1f]. It is %.1f",
                                                             field, lboundIncl, uboundIncl, v));
        }
    }

    public void setStopwordLimit(double limit) {
        validateRange(STOPWORD_LIMIT, limit, 0.0, 1.0);
        stopwordLimit = limit;
    }
    public void setAdjustTarget(double target) {
        validateRange(ADJUST_TARGET, target, 0.0, 1.0);
        adjustTarget = target;
    }
    public void setAllowDropAll(boolean allowDropAll) {
        this.allowDropAll = allowDropAll;
    }

    /** Internal operation - DO NOT USE DIRECTLY */
    public void prepare(RankProperties rankProperties) {
        if (stopwordLimit != null) {
            rankProperties.put("vespa.matching.weakand.stop_word_drop_limit", String.valueOf(stopwordLimit));
        }
        if (adjustTarget != null) {
            rankProperties.put("vespa.matching.weakand.stop_word_adjust_limit", String.valueOf(adjustTarget));
        }
        if (allowDropAll != null) {
            rankProperties.put("vespa.matching.weakand.allow_drop_all", String.valueOf(allowDropAll));
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
        return Objects.equals(stopwordLimit, weakAnd.stopwordLimit) &&
               Objects.equals(adjustTarget, weakAnd.adjustTarget) &&
               Objects.equals(allowDropAll, weakAnd.allowDropAll);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopwordLimit, adjustTarget, allowDropAll);
    }

}
