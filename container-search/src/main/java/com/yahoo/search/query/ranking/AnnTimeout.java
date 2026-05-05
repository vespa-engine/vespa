// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Settings for the ANN-timeout feature.
 *
 * @author boeker
 */
public class AnnTimeout implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String ANNTIMEOUT = "anntimeout";
    public static final String ENABLE = "enable";
    public static final String FACTOR = "factor";

    static {
        argumentType = new QueryProfileType(ANNTIMEOUT);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(ENABLE, "boolean"));
        argumentType.addField(new FieldDescription(FACTOR, "double"));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Boolean enabled = null;
    private Double factor = null;

    public void setEnable(boolean enable) {
        this.enabled = enable;
    }

    /** Returns whether anntimeout is enabled. Default is false.
     * TODO: Make true the default once everything is in place.
     * */
    public Boolean getEnable() {
        if (enabled == null) return Boolean.FALSE;
        return enabled;
    }

    /** Set the factor */
    public void setFactor(double factor) {
        if ((factor < 0.0) || (factor > 1.0)) {
            throw new IllegalInputException("factor must be in the range [0.0, 1.0], got " + factor);
        }
        this.factor = factor;
    }

    public Double getFactor() { return factor; }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {
        if (enabled != null)
            rankProperties.put("vespa.matching.anntimeout.enable", String.valueOf(enabled));
        if (factor != null)
            rankProperties.put("vespa.matching.anntimeout.factor", String.valueOf(factor));
    }

    @Override
    public AnnTimeout clone() {
        try {
            return (AnnTimeout) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, factor);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AnnTimeout other)) return false;

        if (!Objects.equals(this.enabled, other.enabled)) return false;
        if (!Objects.equals(this.factor, other.factor)) return false;

        return true;
    }

}
