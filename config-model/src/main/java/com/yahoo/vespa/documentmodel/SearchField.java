// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * @author baldersheim
 */
public class SearchField extends Field {

    /// Indicate if field shall be stored in memory for attribute usage.
    private boolean attribute = false;
    /// Indicate if the field is Vespa indexed.
    private boolean indexed = false;
    /// Indication to backend on how much optimization should be done.

    /**
     * This is a representation of features to generate for this field.
     * It can be both optimize hints, and real functional hints.
     */
    public enum Feature {
        WEIGHT_IN_ATTRIBUTE_POSTINGS("WeightInAttributePosting"),    // Hint to put the weight in postings for attribute.
        WORDPOS_IN_POSTINGS("WordPosInPosting"),                     // Default for generating posocc
        FILTER_ONLY("FilterOnly");                                   // Might only generate bitvector
        private String name;
        Feature(String name) { this.name = name;}
        public String getName() { return name; }
    }
    private List<Feature> featureList = new ArrayList<>();

    public SearchField(Field field, boolean indexed, boolean attribute) {
        this(field, indexed, attribute, null);
    }
    public SearchField(Field field, boolean indexed, boolean attribute, List<Feature> features) {
        super(field.getName(), field);
        this.attribute = attribute;
        this.indexed = indexed;
        if (features != null) {
            featureList.addAll(features);
        }
        validate();
    }

    private void validate() {
        if (attribute || !indexed) {
            return;
        }
        DataType fieldType = getDataType();
        DataType primiType = fieldType.getPrimitiveType();
        if (DataType.STRING.equals(primiType) || DataType.URI.equals(primiType)) {
            return;
        }
        throw new IllegalArgumentException("Expected type " + DataType.STRING.getName() + " for indexed field '" +
                                           getName() + "', got " + fieldType.getName() + ".");
    }

    public SearchField setIndexed() { indexed = true; validate(); return this; }
    public SearchField setAttribute() { attribute = true; validate(); return this; }
    public boolean isAttribute() { return attribute; }

    public boolean   isIndexed() { return indexed; }
    public SearchField addFeature(Feature feature) { featureList.add(feature); validate(); return this; }
}
