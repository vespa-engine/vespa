// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Wraps {@link ImportedField} as {@link ImmutableSDField}.
 * Methods that are not meaningful or relevant for imported fields will throw {@link UnsupportedOperationException}.
 *
 * @author bjorncs
 */
public class ImmutableImportedSDField implements ImmutableSDField {

    private final ImportedField importedField;

    public ImmutableImportedSDField(ImportedField importedField) {
        this.importedField = importedField;
    }

    public ImportedField getImportedField() {
        return importedField;
    }

    @Override
    public <T extends Expression> boolean containsExpression(Class<T> searchFor) {
        throw createUnsupportedException(searchFor.getSimpleName());
    }

    @Override
    public boolean doesAttributing() {
        return importedField.targetField().doesAttributing();
    }

    @Override
    public boolean doesIndexing() {
        return importedField.targetField().doesIndexing();
    }

    @Override
    public boolean doesLowerCasing() {
        return importedField.targetField().doesLowerCasing();
    }

    @Override
    public boolean isExtraField() {
        return false;
    }

    @Override
    public boolean isHeader() {
        return importedField.targetField().isHeader();
    }

    @Override
    public boolean isImportedField() {
        return true;
    }

    @Override
    public ImmutableSDField getBackingField() { return importedField.targetField(); }

    @Override
    public boolean isIndexStructureField() {
        return importedField.targetField().isIndexStructureField();
    }

    @Override
    public boolean usesStructOrMap() {
        return importedField.targetField().usesStructOrMap();
    }

    @Override
    public DataType getDataType() {
        return importedField.targetField().getDataType();
    }

    @Override
    public Index getIndex(String name) {
        if ( ! importedField.fieldName().equals(name)) {
            throw new IllegalArgumentException("Getting an index (" + name + ") with different name than the imported field ("
                                               + importedField.fieldName() + ") is not supported");
        }
        String targetIndexName = importedField.targetField().getName();
        return importedField.targetField().getIndex(targetIndexName);
    }

    @Override
    public List<String> getQueryCommands() {
        return importedField.targetField().getQueryCommands();
    }

    @Override
    public Map<String, Attribute> getAttributes() {
        return importedField.targetField().getAttributes();
    }

    @Override
    public Map<String, String> getAliasToName() {
        return Collections.emptyMap();
    }

    @Override
    public ScriptExpression getIndexingScript() {
        throw createUnsupportedException("indexing");
    }

    @Override
    public Matching getMatching() {
        return importedField.targetField().getMatching();
    }

    @Override
    public NormalizeLevel getNormalizing() {
        return importedField.targetField().getNormalizing();
    }

    @Override
    public ImmutableSDField getStructField(String name) {
        throw createUnsupportedException("struct");
    }

    @Override
    public Collection<? extends ImmutableSDField> getStructFields() {
        throw createUnsupportedException("struct");
    }

    @Override
    public Stemming getStemming() {
        return importedField.targetField().getStemming();
    }

    @Override
    public Stemming getStemming(Search search) {
        throw createUnsupportedException("stemming");
    }

    @Override
    public Ranking getRanking() {
        throw createUnsupportedException("ranking");
    }

    @Override
    public String getName() {
        return importedField.fieldName(); // Name of the imported field, not the target field
    }

    /**
     * Returns a field representation of the imported field.
     * Changes to the returned instance are not propagated back to the underlying imported field!
     */
    @Override
    public Field asField() {
        return new Field(
                importedField.fieldName(),
                importedField.targetField().getDataType());
    }

    private static UnsupportedOperationException createUnsupportedException(String aspect) {
        return new UnsupportedOperationException("'" + aspect + "' is not meaningful or relevant for an imported field.");
    }

}
