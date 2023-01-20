// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profiling;

import com.yahoo.search.query.Trace;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Contains settings for different parts of the backend query evaluation that should be profiled.
 *
 * @author geirst
 */
public class Profiling implements Cloneable {

    public static final String MATCHING = "matching";
    public static final String FIRST_PHASE_RANKING = "firstPhaseRanking";
    public static final String SECOND_PHASE_RANKING = "secondPhaseRanking";

    private static final QueryProfileType argumentType;

    static {
        argumentType = new QueryProfileType(Trace.PROFILING);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(MATCHING, new QueryProfileFieldType(ProfilingParams.getArgumentType())));
        argumentType.addField(new FieldDescription(FIRST_PHASE_RANKING, new QueryProfileFieldType(ProfilingParams.getArgumentType())));
        argumentType.addField(new FieldDescription(SECOND_PHASE_RANKING, new QueryProfileFieldType(ProfilingParams.getArgumentType())));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    private ProfilingParams matching = new ProfilingParams();
    private ProfilingParams firstPhaseRanking = new ProfilingParams();
    private ProfilingParams secondPhaseRanking = new ProfilingParams();

    public ProfilingParams getMatching() {
        return matching;
    }

    public ProfilingParams getFirstPhaseRanking() {
        return firstPhaseRanking;
    }

    public ProfilingParams getSecondPhaseRanking() {
        return secondPhaseRanking;
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
        Profiling profiling = (Profiling) o;
        return Objects.equals(matching, profiling.matching) &&
                Objects.equals(firstPhaseRanking, profiling.firstPhaseRanking) &&
                Objects.equals(secondPhaseRanking, profiling.secondPhaseRanking);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matching, firstPhaseRanking, secondPhaseRanking);
    }
}
