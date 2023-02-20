// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.api.annotations.Beta;
import com.yahoo.prelude.query.textualrepresentation.TextualQueryRepresentation;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profiling.Profiling;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trace settings and methods for tracing a query.
 * The actual trace is a tree structure stored in the query execution.
 *
 * @author bratseth
 */
@Beta
public class Trace implements Cloneable {

    private static final Logger log = Logger.getLogger(Trace.class.getName());

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String TRACE = "trace";
    public static final String LEVEL = "level";
    public static final String EXPLAIN_LEVEL = "explainLevel";
    public static final String PROFILE_DEPTH = "profileDepth";
    public static final String TIMESTAMPS = "timestamps";
    public static final String QUERY = "query";
    public static final String PROFILING = "profiling";

    static {
        argumentType = new QueryProfileType(TRACE);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(LEVEL, "integer", "tracelevel traceLevel"));
        argumentType.addField(new FieldDescription(EXPLAIN_LEVEL, "integer", "explainlevel explainLevel"));
        argumentType.addField(new FieldDescription(PROFILE_DEPTH, "integer"));
        argumentType.addField(new FieldDescription(TIMESTAMPS, "boolean"));
        argumentType.addField(new FieldDescription(QUERY, "boolean"));
        argumentType.addField(new FieldDescription(PROFILING, new QueryProfileFieldType(Profiling.getArgumentType())));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    private Query parent;

    private int level = 0;
    private int explainLevel = 0;
    private int profileDepth = 0;
    private boolean timestamps = false;
    private boolean query = true;
    private Profiling profiling = new Profiling();

    public Trace(Query parent) {
        this.parent = Objects.requireNonNull(parent);
    }

    /** Returns the level of detail we'll be tracing at in this query. The default level is 0; no tracing. */
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public boolean isTraceable(int level) { return level <= this.level; }

    /** Sets the explain level of this query, 0 means no tracing. Higher numbers means increasingly more explaining. */
    public void setExplainLevel(int explainLevel) { this.explainLevel = explainLevel; }
    public int getExplainLevel() { return explainLevel; }

    /** Sets the profiling depth. Profiling enabled if non-zero. Higher numbers means increasingly more detail. */
    public void setProfileDepth(int profileDepth) {
        this.profileDepth = profileDepth;
        profiling.getMatching().setDepth(profileDepth);
        profiling.getFirstPhaseRanking().setDepth(profileDepth);
        profiling.getSecondPhaseRanking().setDepth(profileDepth);
    }
    public int getProfileDepth() { return profileDepth; }

    /** Returns whether trace entries should have a timestamp. Default is false. */
    public boolean getTimestamps() { return timestamps; }
    public void setTimestamps(boolean timestamps) { this.timestamps = timestamps; }

    /** Returns whether any trace entries should include the query. Default is true. */
    public boolean getQuery() { return query; }
    public void setQuery(boolean query) { this.query = query; }

    public Profiling getProfiling() {
        return profiling;
    }

    /**
     * Adds a context message to this query and to the info log,
     * if the context level of the query is sufficiently high.
     * The context information will be carried over to the result at creation.
     * The message parameter will be included <i>with</i> XML escaping.
     *
     * @param message      the message to add
     * @param traceLevel   the context level of the message, this method will do nothing
     *                     if the traceLevel of the query is lower than this value
     */
    public void trace(String message, int traceLevel) {
        trace(message, false, traceLevel);
    }

    public void trace(Object message, int traceLevel) {
        if ( ! isTraceable(traceLevel)) return;
        parent.getContext(true).trace(message, 0);
    }

    /**
     * Adds a trace message to this query
     * if the trace level of the query is sufficiently high.
     *
     * @param message      the message to add
     * @param includeQuery true to append the query root stringValue at the end of the message
     * @param traceLevel   the context level of the message, this method will do nothing
     *                     if the traceLevel of the query is lower than this value
     */
    public void trace(String message, boolean includeQuery, int traceLevel) {
        if ( ! isTraceable(traceLevel)) return;

        if (includeQuery && query)
            message += ": [" + queryTreeText() + "]";

        log.log(Level.FINE, message);

        // Pass 0 as traceLevel as the trace level check is already done above,
        // and it is not propagated to trace until execution has started
        // (it is done in the execution.search method)
        parent.getContext(true).trace(message, 0);
    }

    /**
     * Adds a trace message to this query
     * if the trace level of the query is sufficiently high.
     *
     * @param includeQuery true to append the query root stringValue at the end of the message
     * @param traceLevel   the context level of the message, this method will do nothing
     *                     if the traceLevel of the query is lower than this value
     * @param messages     the messages whose toStrings will be concatenated into the trace message.
     *                     Concatenation will only happen if the trace level is sufficiently high.
     */
    public void trace(boolean includeQuery, int traceLevel, Object... messages) {
        if ( ! isTraceable(traceLevel)) return;

        StringBuilder concatenated = new StringBuilder();
        for (Object message : messages)
            concatenated.append(message);
        trace(concatenated.toString(), includeQuery, traceLevel);
    }

    /**
     * Traces how properties was resolved and from where. Done after the fact to avoid special handling
     * of tracelevel, which is the property deciding whether this needs to be done
     */
    public void traceProperties() {
        if (level == 0) return;
        CompiledQueryProfile profile = null;
        QueryProfileProperties profileProperties = parent.properties().getInstance(QueryProfileProperties.class);
        if (profileProperties != null)
            profile = profileProperties.getQueryProfile();

        if (profile == null)
            trace("No query profile is used", false, 1);
        else
            trace("Using " + profile, false, 1);

        if (level < 4) return;
        StringBuilder b = new StringBuilder("Resolved properties:\n");
        Set<String> mentioned = new HashSet<>();
        for (Map.Entry<String,String> requestProperty : requestProperties().entrySet() ) {
            Object resolvedValue = parent.properties().get(requestProperty.getKey(), requestProperties());
            if (resolvedValue == null && requestProperty.getKey().equals("queryProfile"))
                resolvedValue = requestProperty.getValue();

            b.append(requestProperty.getKey());
            b.append(": ");
            b.append(resolvedValue); // (may be null)
            b.append(" (");

            if (profile != null && ! profile.isOverridable(new CompoundName(requestProperty.getKey()), requestProperties()))
                b.append("from query profile - unoverridable, ignoring request value");
            else
                b.append("from request");
            b.append(")\n");
            mentioned.add(requestProperty.getKey());
        }
        if (profile != null) {
            appendQueryProfileProperties(profile, mentioned, b);
        }
        trace(b.toString(),false,4);
    }

    private void appendQueryProfileProperties(CompiledQueryProfile profile, Set<String> mentioned, StringBuilder b) {
        for (var property : profile.listValuesWithSources(CompoundName.empty, requestProperties(), parent.properties()).entrySet()) {
            if ( ! mentioned.contains(property.getKey()))
                b.append(property.getKey()).append(": ").append(property.getValue()).append("\n");
        }
    }

    private Map<String, String> requestProperties() {
        return parent.getHttpRequest().propertyMap();
    }

    private String queryTreeText() {
        QueryTree root = parent.getModel().getQueryTree();

        if (level < 2)
            return root.toString();
        if (level < 6)
            return parent.yqlRepresentation();
        else
            return "\n" + parent.yqlRepresentation() + "\n" + new TextualQueryRepresentation(root.getRoot()) + "\n";
    }

    public Trace cloneFor(Query parent) {
        Trace trace = this.clone();
        trace.parent = parent;
        return trace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trace trace = (Trace) o;
        return level == trace.level &&
                explainLevel == trace.explainLevel &&
                profileDepth == trace.profileDepth &&
                timestamps == trace.timestamps &&
                query == trace.query &&
                Objects.equals(profiling, trace.profiling);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, explainLevel, profileDepth, timestamps, query, profiling);
    }

    @Override
    public Trace clone() {
        try {
            return (Trace)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "trace [level: " + level + ", explainLevel: " + explainLevel + ", timestamps: " + timestamps + ", query: " + query + "]";
    }

}
