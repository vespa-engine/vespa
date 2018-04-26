// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.Properties;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.SubstituteString;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A query profile in a state where it is optimized for fast lookups.
 *
 * @author bratseth
 */
public class CompiledQueryProfile extends AbstractComponent implements Cloneable {

    private static final Pattern namePattern=Pattern.compile("[$a-zA-Z_/][-$a-zA-Z0-9_/()]*");

    private final CompiledQueryProfileRegistry registry;

    /** The type of this, or null if none */
    private final QueryProfileType type;

    /** The values of this */
    private final DimensionalMap<CompoundName, Object> entries;

    /** Keys which have a type in this */
    private final DimensionalMap<CompoundName, QueryProfileType> types;

    /** Keys which are (typed or untyped) references to other query profiles in this. Used as a set. */
    private final DimensionalMap<CompoundName, Object> references;

    /** Values which are not overridable in this. Used as a set. */
    private final DimensionalMap<CompoundName, Object> unoverridables;

    /**
     * Creates a new query profile from an id.
     */
    public CompiledQueryProfile(ComponentId id, QueryProfileType type,
                                DimensionalMap<CompoundName, Object> entries,
                                DimensionalMap<CompoundName, QueryProfileType> types,
                                DimensionalMap<CompoundName, Object> references,
                                DimensionalMap<CompoundName, Object> unoverridables,
                                CompiledQueryProfileRegistry registry) {
        super(id);
        this.registry = registry;
        if (type != null)
            type.freeze();
        this.type = type;
        this.entries = entries;
        this.types = types;
        this.references = references;
        this.unoverridables = unoverridables;
        if ( ! id.isAnonymous())
            validateName(id.getName());
    }

    // ----------------- Public API -------------------------------------------------------------------------------

    /** Returns the registry this belongs to, or null if none (in which case runtime profile reference assignment won't work) */
    public CompiledQueryProfileRegistry getRegistry() { return registry; }

    /** Returns the type of this or null if it has no type */
    // TODO: Move into below
    public QueryProfileType getType() { return type; }

    /**
     * Returns whether or not the given field name can be overridden at runtime.
     * Attempts to override values which cannot be overridden will not fail but be ignored.
     * Default: true.
     *
     * @param name the name of the field to check
     * @param context the context in which to check, or null if none
     */
    public final boolean isOverridable(CompoundName name, Map<String, String> context) {
        return unoverridables.get(name, context) == null;
    }

    /** Returns the type of a given prefix reachable from this profile, or null if none */
    public final QueryProfileType getType(CompoundName name, Map<String, String> context) {
        return types.get(name, context);
    }

    /** Returns the types reachable from this, or an empty map (never null) if none */
    public DimensionalMap<CompoundName, QueryProfileType> getTypes() { return types; }

    /** Returns the references reachable from this, or an empty map (never null) if none */
    public DimensionalMap<CompoundName, Object> getReferences() { return references; }

    /**
     * Return all objects that start with the given prefix path using no context. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public final Map<String, Object> listValues(final CompoundName prefix) {  return listValues(prefix, Collections.<String,String>emptyMap()); }
    public final Map<String, Object> listValues(final String prefix) { return listValues(new CompoundName(prefix)); }
    /**
     * Return all objects that start with the given prefix path. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public final Map<String, Object> listValues(final String prefix,Map<String, String> context) {
        return listValues(new CompoundName(prefix), context);
    }
    /**
     * Return all objects that start with the given prefix path. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public final Map<String, Object> listValues(final CompoundName prefix,Map<String, String> context) {
        return listValues(prefix, context, null);
    }
    /**
     * Adds all objects that start with the given path prefix to the given value map. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public Map<String, Object> listValues(CompoundName prefix, Map<String, String> context, Properties substitution) {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<CompoundName, DimensionalValue<Object>> entry : entries.entrySet()) {
            if ( entry.getKey().size() <= prefix.size()) continue;
            if ( ! entry.getKey().hasPrefix(prefix)) continue;

            Object value = entry.getValue().get(context);
            if (value == null) continue;

            value = substitute(value, context, substitution);
            CompoundName suffixName = entry.getKey().rest(prefix.size());
            values.put(suffixName.toString(), value);
        }
        return values;
    }

    public final Object get(String name) {
        return get(name, Collections.emptyMap());
    }
    public final Object get(String name, Map<String, String> context) {
        return get(name, context, new QueryProfileProperties(this));
    }
    public final Object get(String name, Map<String, String> context, Properties substitution) {
        return get(new CompoundName(name), context, substitution);
    }
    public final Object get(CompoundName name, Map<String, String> context, Properties substitution) {
        return substitute(entries.get(name, context), context, substitution);
    }

    private Object substitute(Object value, Map<String, String> context, Properties substitution) {
        if (value == null) return value;
        if (substitution == null) return value;
        if (value.getClass() != SubstituteString.class) return value;
        return ((SubstituteString)value).substitute(context, substitution);
    }

    /** Throws IllegalArgumentException if the given string is not a valid query profile name */
    private static void validateName(String name) {
        Matcher nameMatcher = namePattern.matcher(name);
        if ( ! nameMatcher.matches())
            throw new IllegalArgumentException("Illegal name '" + name + "'");
    }

    @Override
    public CompiledQueryProfile clone() {
        return this; // immutable
    }

    @Override
    public String toString() {
        return "query profile '" + getId()  + "'" + (type!=null ? " of type '" + type.getId() + "'" : "");
    }

}
