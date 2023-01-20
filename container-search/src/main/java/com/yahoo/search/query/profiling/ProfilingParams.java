// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profiling;

import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Contains parameters for a part of the backend query evaluation that should be profiled.
 *
 * @author geirst
 */
public class ProfilingParams {

    public static final String PROFILING_PARAMS = "profilingParams";
    public static final String DEPTH = "depth";

    private static final QueryProfileType argumentType;

    static {
        argumentType = new QueryProfileType(PROFILING_PARAMS);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(DEPTH, "integer"));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    private int depth = 0;

    public int getDepth() {
        return depth;
    }

    public void setDepth(int value) {
        depth = value;
    }

    @Override
    public ProfilingParams clone() {
        try {
            return (ProfilingParams) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Someone inserted a non-cloneable superclass", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfilingParams that = (ProfilingParams) o;
        return Objects.equals(depth, that.depth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(depth);
    }
}
