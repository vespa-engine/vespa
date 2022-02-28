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

    public void convertTypes() {
        convertDataTypes();
        registerDataTypes();
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

    Map<String, DocumentType> documentsInProgress = new HashMap<>();
    Map<String, StructDataType> structsInProgress = new HashMap<>();
    Map<String, AnnotationType> annotationsInProgress = new HashMap<>();

    StructDataType findStructInProgress(String name, ParsedDocument context) {
        var resolved = findStructFrom(context, name);
        if (resolved == null) {
            throw new IllegalArgumentException("no struct named " + name + " in context " + context);
        }
        String structId = resolved.getOwner() + "->" + resolved.name();
        var struct = structsInProgress.get(structId);
        assert(struct != null);
        return struct;
    }

    AnnotationType findAnnotationInProgress(String name, ParsedDocument context) {
        var resolved = findAnnotationFrom(context, name);
        String annotationId = resolved.getOwner() + "->" + resolved.name();
        var annotation = annotationsInProgress.get(annotationId);
        if (annotation == null) {
            throw new IllegalArgumentException("no annotation named " + name + " in context " + context);
        }
        return annotation;
    }

    ParsedStruct findStructFrom(ParsedDocument doc, String name) {
        ParsedStruct found = doc.getStruct(name);
        if (found != null) return found;
        for (var parent : doc.getResolvedInherits()) {
            var fromParent = findStructFrom(parent, name);
            if (fromParent == null) continue;
            if (fromParent == found) continue;
            if (found == null) {
                found = fromParent;
            } else {
                throw new IllegalArgumentException("conflicting values for struct " + name + " in " +doc);
            }
        }
        return found;
    }

    ParsedAnnotation findAnnotationFrom(ParsedDocument doc, String name) {
        ParsedAnnotation found = doc.getAnnotation(name);
        if (found != null) return found;
        for (var parent : doc.getResolvedInherits()) {
            var fromParent = findAnnotationFrom(parent, name);
            if (fromParent == null) continue;
            if (fromParent == found) continue;
            if (found == null) {
                found = fromParent;
            } else {
                throw new IllegalArgumentException("conflicting values for annotation " + name + " in " +doc);
            }
        }
        return found;
    }

    private DataType createArray(ParsedType pType, ParsedDocument context) {
        DataType nested = resolveType(pType.nestedType(), context);
        return DataType.getArray(nested);
    }

    private DataType createWset(ParsedType pType, ParsedDocument context) {
        DataType nested = resolveType(pType.nestedType(), context);
        boolean cine = pType.getCreateIfNonExistent();
        boolean riz = pType.getRemoveIfZero();
        return new WeightedSetDataType(nested, cine, riz);
    }

    private DataType createMap(ParsedType pType, ParsedDocument context) {
        DataType kt = resolveType(pType.mapKeyType(), context);
        DataType vt = resolveType(pType.mapValueType(), context);
        return DataType.getMap(kt, vt);
    }

    private DocumentType findDocInProgress(String name) {
        var dt = documentsInProgress.get(name);
        if (dt == null) {
            throw new IllegalArgumentException("missing document type for: " + name);
        }
        return dt;
    }

    private DataType createAnnRef(ParsedType pType, ParsedDocument context) {
        AnnotationType annotation = findAnnotationInProgress(pType.getNameOfReferencedAnnotation(), context);
        return new AnnotationReferenceDataType(annotation);
    }

    private DataType createDocRef(ParsedType pType) {
        var ref = pType.getReferencedDocumentType();
        assert(ref.getVariant() == ParsedType.Variant.DOCUMENT);
        return ReferenceDataType.createWithInferredId(findDocInProgress(ref.name()));
    }

    DataType resolveType(ParsedType pType, ParsedDocument context) {
        switch (pType.getVariant()) {
        case NONE:     return DataType.NONE;
        case BUILTIN:  return docMan.getDataType(pType.name());
        case POSITION: return PositionDataType.INSTANCE;
        case ARRAY:    return createArray(pType, context);
        case WSET:     return createWset(pType, context);
        case MAP:      return createMap(pType, context);
        case TENSOR:   return DataType.getTensor(pType.getTensorType());
        case DOC_REFERENCE:  return createDocRef(pType);
        case ANN_REFERENCE:  return createAnnRef(pType, context);
        case DOCUMENT: return findDocInProgress(pType.name());
        case STRUCT:   return findStructInProgress(pType.name(), context);
        case UNKNOWN:
            // fallthrough
        }
        // unknown is probably struct, but could be document:
        if (documentsInProgress.containsKey(pType.name())) {
            pType.setVariant(ParsedType.Variant.DOCUMENT);
            return findDocInProgress(pType.name());
        }
        var struct = findStructInProgress(pType.name(), context);
        pType.setVariant(ParsedType.Variant.STRUCT);
        return struct;
    }

    void convertDataTypes() {
        for (var schema : orderedInput) {
            String name = schema.getDocument().name();
            documentsInProgress.put(name, new DocumentType(name));
        }
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var struct : doc.getStructs()) {
                var dt = new StructDataType(struct.name());
                String structId = doc.name() + "->" + struct.name();
                structsInProgress.put(structId, dt);
            }
            for (var annotation : doc.getAnnotations()) {
                String annId = doc.name() + "->" + annotation.name();
                var at = new AnnotationType(annotation.name());
                annotationsInProgress.put(annId, at);
                var withStruct = annotation.getStruct();
                if (withStruct.isPresent()) {
                    var sn = withStruct.get().name();
                    var dt = new StructDataType(sn);
                    String structId = doc.name() + "->" + sn;
                    structsInProgress.put(structId, dt);
                }
            }
        }
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var struct : doc.getStructs()) {
                String structId = doc.name() + "->" + struct.name();
                var toFill = structsInProgress.get(structId);
                for (String inherit : struct.getInherited()) {
                    var parent = findStructInProgress(inherit, doc);
                    toFill.inherit(parent);
                }
                for (ParsedField field : struct.getFields()) {
                    var t = resolveType(field.getType(), doc);
                    var f = new com.yahoo.document.Field(field.name(), t);
                    toFill.addField(f);
                }
            }
            for (var annotation : doc.getAnnotations()) {
                String annId = doc.name() + "->" + annotation.name();
                var at = annotationsInProgress.get(annId);
                var withStruct = annotation.getStruct();
                if (withStruct.isPresent()) {
                    ParsedStruct struct = withStruct.get();
                    String structId = doc.name() + "->" + struct.name();
                    var toFill = structsInProgress.get(structId);
                    for (ParsedField field : struct.getFields()) {
                        var t = resolveType(field.getType(), doc);
                        var f = new com.yahoo.document.Field(field.name(), t);
                        toFill.addField(f);
                    }
                    at.setDataType(toFill);
                }
                for (String inherit : annotation.getInherited()) {
                    var parent = findAnnotationInProgress(inherit, doc);
                    at.inherit(parent);
                }
            }

            var docToFill = documentsInProgress.get(doc.name());
            Map<String, Collection<String>> fieldSets = new HashMap<>();
            List<String> inDocFields = new ArrayList<>();
            for (var docField : doc.getFields()) {
                String name = docField.name();
                var t = resolveType(docField.getType(), doc);
                var f = new com.yahoo.document.Field(name, t);
                docToFill.addField(f);
                inDocFields.add(name);
            }
            fieldSets.put("[document]", inDocFields);
            for (var extraField : schema.getFields()) {
                String name = extraField.name();
                var t = resolveType(extraField.getType(), doc);
                var f = new com.yahoo.document.Field(name, t);
                docToFill.addField(f);
            }
            for (var fieldset : schema.getFieldSets()) {
                fieldSets.put(fieldset.name(), fieldset.getFieldNames());
            }
            docToFill.addFieldSets(fieldSets);
            for (String inherit : doc.getInherited()) {
                docToFill.inherit(findDocInProgress(inherit));
            }
        }
    }

    void registerDataTypes() {
        for (DataType t : structsInProgress.values()) {
            docMan.register(t);
        }
        for (DocumentType t : documentsInProgress.values()) {
            docMan.register(t);
        }
        for (AnnotationType t : annotationsInProgress.values()) {
            docMan.getAnnotationTypeRegistry().register(t);
        }
    }

}
