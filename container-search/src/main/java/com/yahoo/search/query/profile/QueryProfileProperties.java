// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.collections.Pair;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.properties.PropertyMap;
import com.yahoo.protect.Validator;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.DimensionalValue;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Properties backed by a query profile.
 * This has the scope of one query and is not multithread safe.
 *
 * @author bratseth
 */
public class QueryProfileProperties extends Properties {

    private final CompiledQueryProfile profile;

    // Note: The priority order is: values has precedence over references

    /** Values which has been overridden at runtime, or null if none */
    private Map<CompoundName, Object> values = null;
    /** Query profile references which has been overridden at runtime, or null if none. Earlier values has precedence */
    private List<Pair<CompoundName, CompiledQueryProfile>> references = null;

    /** Creates an instance from a profile, throws an exception if the given profile is null */
    public QueryProfileProperties(CompiledQueryProfile profile) {
        Validator.ensureNotNull("The profile wrapped by this cannot be null", profile);
        this.profile = profile;
    }

    /** Returns the query profile backing this, or null if none */
    public CompiledQueryProfile getQueryProfile() { return profile; }

    /** Gets a value from the query profile, or from the nested profile if the value is null */
    @Override
    public Object get(CompoundName name, Map<String,String> context,
                      com.yahoo.processing.request.Properties substitution) {
        name = unalias(name, context);
        Object value = null;
        if (values != null)
            value = values.get(name);
        if (value == null) {
            Pair<CompoundName, CompiledQueryProfile> reference = findReference(name);
            if (reference != null)
                return reference.getSecond().get(name.rest(reference.getFirst().size()), context, substitution); // yes; even if null
        }

        if (value == null)
            value = profile.get(name, context, substitution);
        if (value == null)
            value = super.get(name, context, substitution);
        return value;
    }

    /**
     * Sets a value in this query profile
     *
     * @throws IllegalArgumentException if this property cannot be set in the wrapped query profile
     */
    @Override
    public void set(CompoundName name, Object value, Map<String,String> context) {
        // TODO: Refactor
        try {
            name = unalias(name, context);

            if (context == null)
                context = Collections.emptyMap();

            if ( ! profile.isOverridable(name, context)) return;

            // Check runtime references
            Pair<CompoundName, CompiledQueryProfile> runtimeReference = findReference(name);
            if (runtimeReference != null &&  ! runtimeReference.getSecond().isOverridable(name.rest(runtimeReference.getFirst().size()), context))
                return;

            // Check types
            if ( ! profile.getTypes().isEmpty()) {
                for (int i = 0; i<name.size(); i++) {
                    QueryProfileType type = profile.getType(name.first(i), context);
                    if (type == null) continue;
                    String localName = name.get(i);
                    FieldDescription fieldDescription = type.getField(localName);
                    if (fieldDescription == null && type.isStrict())
                        throw new IllegalArgumentException("'" + localName + "' is not declared in " + type + ", and the type is strict");

                    // TODO: In addition to strictness, check legality along the way

                    if (i == name.size()-1 && fieldDescription != null) { // at the end of the path, check the assignment type
                        value = fieldDescription.getType().convertFrom(value, profile.getRegistry());
                        if (value == null)
                            throw new IllegalArgumentException("'" + value + "' is not a " +
                                                               fieldDescription.getType().toInstanceDescription());
                    }
                }
            }

            if (value instanceof String && value.toString().startsWith("ref:")) {
                if (profile.getRegistry() == null)
                    throw new IllegalArgumentException("Runtime query profile references does not work when the " +
                                                       "QueryProfileProperties are constructed without a registry");
                String queryProfileId = value.toString().substring(4);
                value = profile.getRegistry().findQueryProfile(queryProfileId);
                if (value == null)
                    throw new IllegalArgumentException("Query profile '" + queryProfileId + "' is not found");
            }

            if (value instanceof CompiledQueryProfile) { // this will be due to one of the two clauses above
                if (references == null)
                    references = new ArrayList<>();
                references.add(0, new Pair<>(name, (CompiledQueryProfile)value)); // references set later has precedence - put first
            }
            else {
                if (values == null)
                    values = new HashMap<>();
                values.put(name, value);
            }
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not set '" + name + "' to '" + value + "': " + e.getMessage()); // TODO: Nest instead
        }
    }

    @Override
    public Map<String, Object> listProperties(CompoundName path, Map<String,String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        path = unalias(path, context);
        if (context == null) context = Collections.emptyMap();

        Map<String, Object> properties = profile.listValues(path, context, substitution);
        properties.putAll(super.listProperties(path, context, substitution));

        if (references != null) {
            for (Pair<CompoundName, CompiledQueryProfile> refEntry : references) {
                if ( ! refEntry.getFirst().hasPrefix(path.first(Math.min(refEntry.getFirst().size(), path.size())))) continue;

                CompoundName pathInReference;
                CompoundName prefixToReferenceKeys;
                if (refEntry.getFirst().size() > path.size()) {
                    pathInReference = CompoundName.empty;
                    prefixToReferenceKeys = refEntry.getFirst().rest(path.size());
                }
                else {
                    pathInReference = path.rest(refEntry.getFirst().size());
                    prefixToReferenceKeys = CompoundName.empty;
                }
                for (Map.Entry<String, Object> valueEntry : refEntry.getSecond().listValues(pathInReference, context, substitution).entrySet()) {
                    properties.put(prefixToReferenceKeys.append(new CompoundName(valueEntry.getKey())).toString(), valueEntry.getValue());
                }
            }

        }

        if (values != null) {
            for (Map.Entry<CompoundName, Object> entry : values.entrySet()) {
                if (entry.getKey().hasPrefix(path))
                    properties.put(entry.getKey().rest(path.size()).toString(), entry.getValue());
            }
        }

        return properties;
    }

    public boolean isComplete(StringBuilder firstMissingName, Map<String,String> context) {
        // Are all types reachable from this complete?
        if ( ! reachableTypesAreComplete(CompoundName.empty, profile, firstMissingName, context))
            return false;

        // Are all runtime references in this complete?
        if (references == null) return true;
        for (Pair<CompoundName, CompiledQueryProfile> reference : references) {
            if ( ! reachableTypesAreComplete(reference.getFirst(), reference.getSecond(), firstMissingName, context))
                return false;
        }

        return true;
    }

    private boolean reachableTypesAreComplete(CompoundName prefix, CompiledQueryProfile profile, StringBuilder firstMissingName, Map<String,String> context) {
        for (Map.Entry<CompoundName, DimensionalValue<QueryProfileType>> typeEntry : profile.getTypes().entrySet()) {
            QueryProfileType type = typeEntry.getValue().get(context);
            if (type == null) continue;
            if ( ! typeIsComplete(prefix.append(typeEntry.getKey()), type, firstMissingName, context))
                return false;
        }
        return true;
    }

    private boolean typeIsComplete(CompoundName prefix, QueryProfileType type, StringBuilder firstMissingName, Map<String,String> context) {
        if (type == null) return true;
        for (FieldDescription field : type.fields().values()) {
            if ( ! field.isMandatory()) continue;

            CompoundName fieldName = prefix.append(field.getName());
            if ( get(fieldName, null) != null) continue;
            if ( hasReference(fieldName)) continue;

            if (profile.getReferences().get(fieldName, context) != null) continue;

            if (firstMissingName != null)
                firstMissingName.append(fieldName);
            return false;
        }
        return true;
    }

    private boolean hasReference(CompoundName name) {
        if (references == null) return false;
        for (Pair<CompoundName, CompiledQueryProfile> reference : references)
            if (reference.getFirst().equals(name))
                return true;
        return false;
    }

    private Pair<CompoundName, CompiledQueryProfile> findReference(CompoundName name) {
        if (references == null) return null;
        for (Pair<CompoundName, CompiledQueryProfile> entry : references) {
            if (name.hasPrefix(entry.getFirst())) return entry;
        }
        return null;
    }

    CompoundName unalias(CompoundName name, Map<String,String> context) {
        if (profile.getTypes().isEmpty()) return name;

        CompoundName unaliasedName = name;
        for (int i = 0; i<name.size(); i++) {
            QueryProfileType type = profile.getType(name.first(i), context);
            if (type == null) continue;
            if (type.aliases() == null) continue; // TODO: Make never null
            if (type.aliases().isEmpty()) continue;
            String localName = name.get(i);
            String unaliasedLocalName = type.unalias(localName);
            unaliasedName = unaliasedName.set(i, unaliasedLocalName);
        }
        return unaliasedName;
    }

    @Override
    public QueryProfileProperties clone() {
        QueryProfileProperties clone = (QueryProfileProperties)super.clone();
        if (this.values != null)
            clone.values = PropertyMap.cloneMap(this.values);
        return clone;
    }

}
