// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class converting a collection of schemas from the intermediate format.
 * For now only conversion to DocumentType (with contents).
 *
 * @author arnej27959
 **/
public class ConvertSchemaCollection {

    private final IntermediateCollection input;
    private final List<ParsedSchema> orderedInput = new ArrayList<>();
    private final DocumentTypeManager docMan;

    public ConvertSchemaCollection(IntermediateCollection input,
                                   DocumentTypeManager documentTypeManager)
    {
        this.input = input;
        this.docMan = documentTypeManager;
        order();
        pushTypesToDocuments();
    }

    void order() {
        var map = input.getParsedSchemas();
        for (var schema : map.values()) {
            findOrdering(schema);
        }
    }

    void findOrdering(ParsedSchema schema) {
        if (orderedInput.contains(schema)) return;
        for (var parent : schema.getAllResolvedInherits()) {
            findOrdering(parent);
        }
        orderedInput.add(schema);
    }

    void pushTypesToDocuments() {
        for (var schema : orderedInput) {
            for (var struct : schema.getStructs()) {
                schema.getDocument().addStruct(struct);
            }
            for (var annotation : schema.getAnnotations()) {
                schema.getDocument().addAnnotation(annotation);
            }
        }
    }

    public void convertTypes() {
        var converter = new ConvertParsedTypes(orderedInput, docMan);
        converter.convert();
    }
}
