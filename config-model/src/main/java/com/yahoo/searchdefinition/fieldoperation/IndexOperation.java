// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Index.Type;
import com.yahoo.searchdefinition.document.BooleanIndexDefinition;
import com.yahoo.searchdefinition.document.HnswIndexParams;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * @author Einar M R Rosenvinge
 */
public class IndexOperation implements FieldOperation {

    private String indexName;
    private Optional<Boolean> prefix = Optional.empty();
    private List<String> aliases = new LinkedList<>();
    private Optional<String> stemming = Optional.empty();
    private Optional<Type> type = Optional.empty();

    private OptionalInt arity = OptionalInt.empty(); // For predicate data type
    private OptionalLong lowerBound = OptionalLong.empty();
    private OptionalLong upperBound = OptionalLong.empty();
    private OptionalDouble densePostingListThreshold = OptionalDouble.empty();
    private Optional<Boolean> enableBm25 = Optional.empty();

    private Optional<HnswIndexParams.Builder> hnswIndexParams = Optional.empty();

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public boolean getPrefix() {
        return prefix.get();
    }

    public void setPrefix(Boolean prefix) {
        this.prefix = Optional.of(prefix);
    }

    public void addAlias(String alias) {
        aliases.add(alias);
    }

    public String getStemming() {
        return stemming.get();
    }

    public void setStemming(String stemming) {
        this.stemming = Optional.of(stemming);
    }

    public void apply(SDField field) {
        Index index = field.getIndex(indexName);

        if (index == null) {
            index = new Index(indexName);
            field.addIndex(index);
        }

        applyToIndex(index);
    }

    public void applyToIndex(Index index) {
        if (prefix.isPresent()) {
            index.setPrefix(prefix.get());
        }
        for (String alias : aliases) {
            index.addAlias(alias);
        }
        if (stemming.isPresent()) {
            index.setStemming(Stemming.get(stemming.get()));
        }
        if (type.isPresent()) {
            index.setType(type.get());
        }
        if (arity.isPresent() || lowerBound.isPresent() ||
                upperBound.isPresent() || densePostingListThreshold.isPresent()) {
            index.setBooleanIndexDefiniton(
                    new BooleanIndexDefinition(arity, lowerBound, upperBound, densePostingListThreshold));
        }
        if (enableBm25.isPresent()) {
            index.setInterleavedFeatures(enableBm25.get());
        }
        if (hnswIndexParams.isPresent()) {
            index.setHnswIndexParams(hnswIndexParams.get().build());
        }
    }

    public Type getType() {
        return type.get();
    }

    public void setType(Type type) {
        this.type = Optional.of(type);
    }

    public void setArity(int arity) {
        this.arity = OptionalInt.of(arity);
    }

    public void setLowerBound(long value) {
        this.lowerBound = OptionalLong.of(value);
    }

    public void setUpperBound(long value) {
        this.upperBound = OptionalLong.of(value);
    }

    public void setDensePostingListThreshold(double densePostingListThreshold) {
        this.densePostingListThreshold = OptionalDouble.of(densePostingListThreshold);
    }

    public void setEnableBm25(boolean value) {
        enableBm25 = Optional.of(value);
    }

    public void setHnswIndexParams(HnswIndexParams.Builder params) {
        this.hnswIndexParams = Optional.of(params);
    }

}
