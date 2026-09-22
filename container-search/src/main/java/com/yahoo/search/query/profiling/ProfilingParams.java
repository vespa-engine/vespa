// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private static final QueryProfileType argumentType = createArgumentType(PROFILING_PARAMS);

    /**
     * Nested profiling categories cannot share one type: {@code Query.setFrom} builds the
     * property path from the type id, not the field name. Each category therefore needs a
     * type named after its field ({@code matching}, {@code sortFeatures}, …).
     */
    static QueryProfileType createArgumentType(String name) {
        QueryProfileType type = new QueryProfileType(name);
        type.setStrict(true);
        type.setBuiltin(true);
        type.addField(new FieldDescription(DEPTH, "integer"));
        type.freeze();
        return type;
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    private int depth = 0;
    private boolean explicitDepth = false;

    public int getDepth() {
        return depth;
    }

    public void setDepth(int value) {
        depth = value;
        explicitDepth = true;
    }

    void applyDefaultDepth(int value) {
        if (!explicitDepth)
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
