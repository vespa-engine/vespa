// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.api.annotations.Beta;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Information about a schema which is part of the application running this.
 *
 * This is immutable.
 *
 * @author bratseth
 */
@Beta
public class Schema {

    private final String name;
    private final Map<String, Field> fields;
    private final Map<String, FieldSet> fieldSets;
    private final Map<String, RankProfile> rankProfiles;
    private final Map<String, DocumentSummary> documentSummaries;

    /** Fields indexed by both name and aliases. */
    private final Map<String, Field> fieldsByAliases;

    private Schema(Builder builder) {
        this.name = builder.name;
        this.fields = Collections.unmodifiableMap(builder.fields);
        this.fieldSets = Collections.unmodifiableMap(builder.fieldSets);
        this.rankProfiles = Collections.unmodifiableMap(builder.rankProfiles);
        this.documentSummaries = Collections.unmodifiableMap(builder.documentSummaries);

        fieldSets.values().forEach(fieldSet -> fieldSet.setSchema(this));
        rankProfiles.values().forEach(rankProfile -> rankProfile.setSchema(this));

        fieldsByAliases = new HashMap<>();
        for (Field field : fields.values()) {
            fieldsByAliases.put(field.name(), field);
            field.aliases().forEach(alias -> fieldsByAliases.put(alias, field));
        }
    }

    public String name() { return name; }
    public Map<String, Field> fields() { return fields; }
    public Map<String, RankProfile> rankProfiles() { return rankProfiles; }
    public Map<String, DocumentSummary> documentSummaries() { return documentSummaries; }

    /**
     * Looks up a field or field set by the given name or alias in this schema.
     *
     * @param fieldName the name or alias of the field or field set. If this is empty, the name "default" is looked up
     * @return information about the field or field set with the given name, or empty if no item with this name exists
     */
    public Optional<FieldInfo> fieldInfo(String fieldName) {
        if (fieldName.isEmpty())
            fieldName = "default";
        Field field = fieldsByAliases.get(fieldName);
        if (field != null) return Optional.of(field);
        return Optional.ofNullable(fieldSets.get(fieldName));
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Schema other)) return false;
        if ( ! other.name.equals(this.name)) return false;
        if ( ! other.fields.equals(this.fields)) return false;
        if ( ! other.fieldSets.equals(this.fieldSets)) return false;
        if ( ! other.rankProfiles.equals(this.rankProfiles)) return false;
        if ( ! other.documentSummaries.equals(this.documentSummaries)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, rankProfiles, documentSummaries);
    }

    @Override
    public String toString() {
        return "schema '" + name + "'";
    }

    public static class Builder {

        private final String name;
        private final Map<String, Field> fields = new LinkedHashMap<>();
        private final Map<String, FieldSet> fieldSets = new LinkedHashMap<>();
        private final Map<String, RankProfile> rankProfiles = new LinkedHashMap<>();
        private final Map<String, DocumentSummary> documentSummaries = new LinkedHashMap<>();

        public Builder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public Builder add(Field field) {
            fields.put(field.name(), field);
            return this;
        }

        public Builder add(FieldSet fieldSet) {
            fieldSets.put(fieldSet.name(), fieldSet);
            return this;
        }

        public Builder add(RankProfile profile) {
            rankProfiles.put(profile.name(), profile);
            return this;
        }

        public Builder add(DocumentSummary documentSummary) {
            documentSummaries.put(documentSummary.name(), documentSummary);
            return this;
        }

        public Schema build() {
            return new Schema(this);
        }

    }

}
