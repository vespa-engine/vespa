// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.BooleanIndexDefinition;
import com.yahoo.schema.document.HnswIndexParams;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.Stemming;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An index definition in a search definition.
 * Two indices are equal if they have the same name and the same settings, except
 * alias settings (which are excluded).
 *
 * @author bratseth
 */
public class Index implements Cloneable, Serializable {

    public enum Type {

        VESPA("vespa");
        private final String name;
        Type(String name) { this.name = name; }
        public String getName() { return name; }

    }

    // Please see hashCode, equals and copy when adding attributes to this

    /** The search definition-unique name of this index */
    private String name;

    /** The rank type of this index */
    private RankType rankType = null;

   /** Whether this index supports prefix search */
    private boolean prefix;

    /** The list of aliases (Strings) to this index name */
    private Set<String> aliases = new java.util.LinkedHashSet<>(1);

    /**
     * The stemming setting of this field, or null to use the default.
     * Default is determined by the owning search definition.
     */
    private Stemming stemming = null;

    private Type type = Type.VESPA;

    /** The boolean index definition, if set */
    private BooleanIndexDefinition boolIndex;

    private Optional<HnswIndexParams> hnswIndexParams = Optional.empty();

    /** Whether the posting lists of this index field should have interleaved features (num occs, field length) in document id stream. */
    private boolean interleavedFeatures = false;

    public Index(String name) {
        this(name, false);
    }

    public Index(String name, boolean prefix) {
        this.name = name;
        this.prefix = prefix;
    }

    public void setName(String name) { this.name = name; }

    public String getName() { return name; }

    /** Sets the rank type of this field */
    public void setRankType(RankType rankType) { this.rankType = rankType; }

    /** Returns the rank type of this field, or null if nothing is set */
    public RankType getRankType() { return rankType; }

    /** Return the stemming setting of this index, may be null */
    public Stemming getStemming() { return stemming; }

    /**
     * Whether this field should be stemmed in this search definition,
     * this is never null
     */
    public Stemming getStemming(Schema schema) {
        if (stemming != null)
            return stemming;
        else
            return schema.getStemming();
    }

    /**
     * Sets how this field should be stemmed, or set to null to use the default.
     */
    public void setStemming(Stemming stemming) { this.stemming = stemming; }

    /** Returns whether this index supports prefix search, default is false */
    public boolean isPrefix() { return prefix; }

    /** Sets whether this index supports prefix search */
    public void setPrefix(boolean prefix) { this.prefix=prefix; }

    /** Adds an alias to this index name */
    public void addAlias(String alias) {
        aliases.add(alias);
    }

    /** Returns a read-only iterator of the aliases (Strings) to this index name */
    public Iterator<String> aliasIterator() {
        return Collections.unmodifiableSet(aliases).iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return prefix == index.prefix &&
               interleavedFeatures == index.interleavedFeatures &&
               Objects.equals(name, index.name) &&
               rankType == index.rankType &&
               Objects.equals(aliases, index.aliases) &&
               stemming == index.stemming &&
               type == index.type &&
               Objects.equals(boolIndex, index.boolIndex) &&
               Objects.equals(hnswIndexParams, index.hnswIndexParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, rankType, prefix, aliases, stemming, type, boolIndex, hnswIndexParams, interleavedFeatures);
    }

    public String toString() {
        String rankTypeName = rankType == null ? "(none)" : rankType.name();
        return "index '" + name +
               "' [ranktype: " + rankTypeName +
               ", prefix: " + prefix + "]";
    }

    /** Makes a deep copy of this index */
    @Override
    public Object clone() {
        try {
            Index copy = (Index)super.clone();
            copy.aliases = new LinkedHashSet<>(this.aliases);
            return copy;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error",e);
        }
    }

    public Index copy() {
        return (Index)clone();
    }

    /** Returns the index engine type */
    public Type getType() {
        return type;
    }

    /** Sets the index engine type */
    public void setType(Type type) {
        this.type = type;
    }

    /** Returns the boolean index definition */
    public BooleanIndexDefinition getBooleanIndexDefiniton() {
        return boolIndex;
    }

    /** Sets the boolean index definition */
    public void setBooleanIndexDefiniton(BooleanIndexDefinition def) {
        boolIndex = def;
    }

    public Optional<HnswIndexParams> getHnswIndexParams() {
        return hnswIndexParams;
    }

    public void setHnswIndexParams(HnswIndexParams params) {
        hnswIndexParams = Optional.of(params);
    }

    public void setInterleavedFeatures(boolean value) {
        interleavedFeatures = value;
    }

    public boolean useInterleavedFeatures() {
        return interleavedFeatures;
    }

}
