// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import ai.vespa.cloud.ZoneInfo;
import com.yahoo.collections.Pair;
import com.yahoo.language.process.Embedder;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.properties.PropertyMap;
import com.yahoo.protect.Validator;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.DimensionalValue;
import com.yahoo.search.query.profile.types.ConversionContext;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.tensor.Tensor;

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

    private static final String ENVIRONMENT = "environment";
    private static final String REGION = "region";
    private static final String INSTANCE = "instance";
    private final CompiledQueryProfile profile;
    private final Map<String, Embedder> embedders;
    private final ZoneInfo zoneInfo;
    private final Map<String, String> zoneContext;

    // Note: The priority order is: values has precedence over references

    /** Values which has been overridden at runtime, or null if none */
    private Map<CompoundName, Object> values = null;

    /**
     * Query profile references which has been overridden at runtime, possibly to the null value to clear values,
     * or null if none (i.e this is lazy).
     * Earlier values has precedence
     */
    private List<Pair<CompoundName, CompiledQueryProfile>> references = null;

    public QueryProfileProperties(CompiledQueryProfile profile) {
        this(profile, Embedder.throwsOnUse.asMap(), ZoneInfo.defaultInfo());
    }

    @Deprecated // TODO: Remove on Vespa 9
    public QueryProfileProperties(CompiledQueryProfile profile, Embedder embedder) {
        this(profile, Map.of(Embedder.defaultEmbedderId, embedder), ZoneInfo.defaultInfo());
    }

    /** Creates an instance from a profile, throws an exception if the given profile is null */
    @Deprecated // TODO: Remove on Vespa 9
    public QueryProfileProperties(CompiledQueryProfile profile, Map<String, Embedder> embedders) {
        this(profile, embedders, ZoneInfo.defaultInfo());
    }

    /** Creates an instance from a profile, throws an exception if the given profile is null */
    public QueryProfileProperties(CompiledQueryProfile profile, Map<String, Embedder> embedders, ZoneInfo zoneInfo) {
        Validator.ensureNotNull("The profile wrapped by this cannot be null", profile);
        this.profile = profile;
        this.embedders = embedders;
        this.zoneInfo = zoneInfo;
        this.zoneContext = Map.of(
                ENVIRONMENT, zoneInfo.zone().environment().name(),
                REGION, zoneInfo.zone().region(),
                INSTANCE, zoneInfo.application().instance());

    }

    /** Returns the query profile backing this, or null if none */
    public CompiledQueryProfile getQueryProfile() { return profile; }

    /** Gets a value from the query profile, or from the nested profile if the value is null */
    @Override
    public Object get(CompoundName name, Map<String, String> context,
                      com.yahoo.processing.request.Properties substitution) {
        context = contextWithZoneInfo(context);
        name = unalias(name, context);
        if (values != null && values.containsKey(name))
            return values.get(name); // Returns this value, even if null

        Pair<CompoundName, CompiledQueryProfile> reference = findReference(name);
        if (reference != null) {
            if (reference.getSecond() == null)
                return null; // cleared
            else
                return reference.getSecond().get(name.rest(reference.getFirst().size()), context, substitution); // even if null
        }

        Object value = profile.get(name, context, substitution);
        if (value != null)
            return value;
        return super.get(name, context, substitution);
    }

    /**
     * Sets a value in this query profile
     *
     * @throws IllegalArgumentException if this property cannot be set in the wrapped query profile
     */
    @Override
    public void set(CompoundName name, Object value, Map<String, String> context) {
        context = contextWithZoneInfo(context);
        setOrCheckSettable(name, value, context, true);
    }

    @Override
    public void requireSettable(CompoundName name, Object value, Map<String, String> context) {
        context = contextWithZoneInfo(context);
        setOrCheckSettable(name, value, context, false);
    }

    private void setOrCheckSettable(CompoundName name, Object value, Map<String, String> context, boolean set) {
        try {
            name = unalias(name, context);

            if (context == null)
                context = Collections.emptyMap();

            if ( ! profile.isOverridable(name, context)) return;

            // Check runtime references
            Pair<CompoundName, CompiledQueryProfile> runtimeReference = findReference(name);
            if (runtimeReference != null &&  ! runtimeReference.getSecond().isOverridable(name.rest(runtimeReference.getFirst().size()), context))
                return;

            if ( ! profile.getTypes().isEmpty())
                value = convertByType(name, value, context);

            // TODO: On Vespa 9, only support this when the profile is typed and this field has a query profile type
            if (value instanceof String && value.toString().startsWith("ref:")) {
                if (profile.getRegistry() == null)
                    throw new IllegalInputException("Runtime query profile references does not work when the " +
                                                    "QueryProfileProperties are constructed without a registry");
                String queryProfileId = value.toString().substring(4);
                var referencedProfile = profile.getRegistry().findQueryProfile(queryProfileId);
                if (referencedProfile != null)
                    value = referencedProfile;
            }

            if (set) {
                if (value instanceof CompiledQueryProfile) { // this will be due to one of the two clauses above
                    if (references == null)
                        references = new ArrayList<>();
                    // references set later has precedence - put first
                    references.add(0, new Pair<>(name, (CompiledQueryProfile) value));
                } else {
                    if (values == null)
                        values = new HashMap<>();
                    values.put(name, value);
                }
            }
        }
        catch (IllegalArgumentException e) {
            throw new IllegalInputException("Could not set '" + name + "' to '" + toShortString(value) + "'", e);
        }
    }

    private String toShortString(Object value) {
        if (value == null) return "null";
        if ( ! (value instanceof Tensor)) return value.toString();
        return ((Tensor)value).toAbbreviatedString();
    }

    private Object convertByType(CompoundName name, Object value, Map<String, String> context) {
        QueryProfileType type;
        QueryProfileType explicitTypeFromField = null;
        for (int i = 0; i < name.size(); i++) {
            if (explicitTypeFromField != null)
                type = explicitTypeFromField;
            else
                type = profile.getType(name.first(i), context);
            if (type == null) continue;

            String localName = name.get(i);
            FieldDescription fieldDescription = type.getField(localName);
            if (fieldDescription == null && type.isStrict())
                throw new IllegalInputException("'" + localName + "' is not declared in " + type + ", and the type is strict");
            // TODO: In addition to strictness, check legality along the way

            if (fieldDescription != null) {
                if (i == name.size() - 1) { // at the end of the path, check the assignment type
                    var conversionContext = new ConversionContext(localName, profile.getRegistry(), embedders, context);
                    var convertedValue = fieldDescription.getType().convertFrom(value, conversionContext);
                    if (convertedValue == null
                        && fieldDescription.getType() instanceof QueryProfileFieldType
                        && ((QueryProfileFieldType) fieldDescription.getType()).getQueryProfileType() != null) {
                        // Try the value of the query profile itself instead
                        var queryProfileValueDescription = ((QueryProfileFieldType) fieldDescription.getType()).getQueryProfileType().getField("");
                        if (queryProfileValueDescription != null) {
                            convertedValue = queryProfileValueDescription.getType().convertFrom(value, conversionContext);
                            if (convertedValue == null)
                                throw new IllegalInputException("'" + value + "' is neither a " +
                                                                fieldDescription.getType().toInstanceDescription() + " nor a " +
                                                                queryProfileValueDescription.getType().toInstanceDescription());
                        }
                    } else if (convertedValue == null)
                        throw new IllegalInputException("'" + value + "' is not a " +
                                                        fieldDescription.getType().toInstanceDescription());

                    value = convertedValue;
                } else if (fieldDescription.getType() instanceof QueryProfileFieldType) {
                    // If a type is specified, use that instead of the type implied by the name
                    explicitTypeFromField = ((QueryProfileFieldType) fieldDescription.getType()).getQueryProfileType();
                }
            }

        }
        return value;
    }

    @Override
    public void clearAll(CompoundName name, Map<String, String> context) {
        if (references == null)
            references = new ArrayList<>();
        references.add(new Pair<>(name, null));

        if (values != null)
            values.keySet().removeIf(key -> key.hasPrefix(name));
    }

    @Override
    public Map<String, Object> listProperties(CompoundName path, Map<String, String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        context = contextWithZoneInfo(context);

        path = unalias(path, context);
        if (context == null) context = Collections.emptyMap();

        Map<String, Object> properties = new HashMap<>();
        for (var entry : profile.listValues(path, context, substitution).entrySet()) {
            if (references != null && containsNullParentOf(path, references)) continue;
            properties.put(entry.getKey(), entry.getValue());
        }
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
                if (refEntry.getSecond() == null) {
                    if (refEntry.getFirst().hasPrefix(path))
                        properties.put(prefixToReferenceKeys.toString(), null);
                }
                else {
                    for (Map.Entry<String, Object> valueEntry : refEntry.getSecond().listValues(pathInReference, context, substitution).entrySet()) {
                        properties.put(prefixToReferenceKeys.append(new CompoundName(valueEntry.getKey())).toString(), valueEntry.getValue());
                    }
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

    public boolean isComplete(StringBuilder firstMissingName, Map<String, String> context) {
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

    private Map<String, String> contextWithZoneInfo(Map<String, String> context) {
        if (zoneInfo == ZoneInfo.defaultInfo()) return context;
        if (context == null || context.isEmpty()) return zoneContext;
        if (context == zoneContext) return context;
        return new ChainedMap(context, zoneContext);
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

    private boolean containsNullParentOf(CompoundName path, List<Pair<CompoundName, CompiledQueryProfile>> properties) {
        if (properties.contains(new Pair<>(path, (CompiledQueryProfile)null))) return true;
        if (path.size() > 0 && containsNullParentOf(path.first(path.size() - 1), properties)) return true;
        return false;
    }

    CompoundName unalias(CompoundName name, Map<String,String> context) {
        if (profile.getTypes().isEmpty()) return name;

        CompoundName unaliasedName = name;
        for (int i = 0; i < name.size(); i++) {
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
