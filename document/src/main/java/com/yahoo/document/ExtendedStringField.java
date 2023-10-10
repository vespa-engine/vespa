// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

import java.util.HashMap;
import java.util.Map;

/**
 * This adds an Extractor to the ExtendedField that can be used to get access the backed spantrees
 * used in the concrete document types.
 * @author baldersheim
 */
public class ExtendedStringField extends ExtendedField {
    public interface ExtractSpanTrees {
        Map<String, SpanTree> get(StructuredFieldValue doc);
        void set(StructuredFieldValue doc, Map<String, SpanTree> trees);
    }
    private final ExtractSpanTrees extractSpanTrees;
    public ExtendedStringField(String name, DataType type, Extract extract, ExtractSpanTrees extractSpanTrees) {
        super(name, type, extract);
        this.extractSpanTrees = extractSpanTrees;
    }

    @Override
    public FieldValue getFieldValue(StructuredFieldValue doc) {
        StringFieldValue sfv = (StringFieldValue) super.getFieldValue(doc);
        Map<String, SpanTree> trees = extractSpanTrees.get(doc);
        if (trees != null) {
            for (SpanTree tree : trees.values()) {
                sfv.setSpanTree(tree);
            }
        }
        return sfv;
    }

    @Override
    public FieldValue setFieldValue(StructuredFieldValue doc, FieldValue fv) {
        FieldValue old = getFieldValue(doc);
        StringFieldValue sfv = (StringFieldValue) fv;
        super.setFieldValue(doc, sfv);
        Map<String, SpanTree> trees = null;
        if (sfv != null) {
            trees = sfv.getSpanTreeMap();
            if (trees == null) {
                trees = new HashMap<>();
            }
        }
        extractSpanTrees.set(doc, trees);
        return old;
    }

}
