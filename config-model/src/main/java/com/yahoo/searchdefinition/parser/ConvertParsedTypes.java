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
 * Helper class for converting ParsedType instances to DataType
 *
 * @author arnej27959
 **/
public class ConvertParsedTypes {

    private final List<ParsedSchema> orderedInput;
    private final DocumentTypeManager docMan;

    ConvertParsedTypes(List<ParsedSchema> input) {
        this.orderedInput = input;
        this.docMan = new DocumentTypeManager();
    }

    public ConvertParsedTypes(List<ParsedSchema> input, DocumentTypeManager docMan) {
        this.orderedInput = input;
        this.docMan = docMan;
    }

    public void convert(boolean andRegister) {
        startDataTypes();
        fillDataTypes();
        if (andRegister) {
            registerDataTypes();
        }
    }

    private Map<String, DocumentType> documentsFromSchemas = new HashMap<>();
    private Map<String, StructDataType> structsFromSchemas = new HashMap<>();
    private Map<String, AnnotationType> annotationsFromSchemas = new HashMap<>();

    private void startDataTypes() {
        for (var schema : orderedInput) {
            String name = schema.getDocument().name();
            documentsFromSchemas.put(name, new DocumentType(name));
        }
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var struct : doc.getStructs()) {
                String structId = doc.name() + "->" + struct.name();
                // int id = new StructDataType(structId).getId();
                // var dt = new StructDataType(id, struct.name());
                var dt = new StructDataType(struct.name());
                structsFromSchemas.put(structId, dt);
            }
            for (var annotation : doc.getAnnotations()) {
                String annId = doc.name() + "->" + annotation.name();
                var at = new AnnotationType(annotation.name());
                annotationsFromSchemas.put(annId, at);
                var withStruct = annotation.getStruct();
                if (withStruct.isPresent()) {
                    var sn = withStruct.get().name();
                    var dt = new StructDataType(sn);
                    String structId = doc.name() + "->" + sn;
                    structsFromSchemas.put(structId, dt);
                }
            }
        }
    }

    private void fillDataTypes() {
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var struct : doc.getStructs()) {
                String structId = doc.name() + "->" + struct.name();
                var toFill = structsFromSchemas.get(structId);
                // evil ugliness
                for (ParsedField field : struct.getFields()) {
                    if (! field.hasIdOverride()) {
                        var t = resolveFromContext(field.getType(), doc);
                        var f = new com.yahoo.document.Field(field.name(), t);
                        toFill.addField(f);
                    }
                }
                for (ParsedField field : struct.getFields()) {
                    if (field.hasIdOverride()) {
                        var t = resolveFromContext(field.getType(), doc);
                        var f = new com.yahoo.document.Field(field.name(), field.idOverride(), t);
                        toFill.addField(f);
                    }
                }
                for (String inherit : struct.getInherited()) {
                    var parent = findStructFromSchemas(inherit, doc);
                    toFill.inherit(parent);
                }
            }
            for (var annotation : doc.getAnnotations()) {
                String annId = doc.name() + "->" + annotation.name();
                var at = annotationsFromSchemas.get(annId);
                var withStruct = annotation.getStruct();
                if (withStruct.isPresent()) {
                    ParsedStruct struct = withStruct.get();
                    String structId = doc.name() + "->" + struct.name();
                    var toFill = structsFromSchemas.get(structId);
                    for (ParsedField field : struct.getFields()) {
                        var t = resolveFromContext(field.getType(), doc);
                        var f = field.hasIdOverride()
                            ? new com.yahoo.document.Field(field.name(), field.idOverride(), t)
                            : new com.yahoo.document.Field(field.name(), t);
                        toFill.addField(f);
                    }
                    at.setDataType(toFill);
                }
                for (String inherit : annotation.getInherited()) {
                    var parent = findAnnotationFromSchemas(inherit, doc);
                    at.inherit(parent);
                }
            }
            var docToFill = documentsFromSchemas.get(doc.name());
            Map<String, Collection<String>> fieldSets = new HashMap<>();
            List<String> inDocFields = new ArrayList<>();
            for (var docField : doc.getFields()) {
                String name = docField.name();
                var t = resolveFromContext(docField.getType(), doc);
                var f = new com.yahoo.document.Field(docField.name(), t);
                docToFill.addField(f);
                if (docField.hasIdOverride()) {
                    f.setId(docField.idOverride(), docToFill);
                }
                inDocFields.add(name);
            }
            fieldSets.put("[document]", inDocFields);
            for (var extraField : schema.getFields()) {
                String name = extraField.name();
                var t = resolveFromContext(extraField.getType(), doc);
                var f = new com.yahoo.document.Field(name, t);
                docToFill.addField(f);
            }
            for (var fieldset : schema.getFieldSets()) {
                fieldSets.put(fieldset.name(), fieldset.getFieldNames());
            }
            docToFill.addFieldSets(fieldSets);
            for (String inherit : doc.getInherited()) {
                docToFill.inherit(findDocFromSchemas(inherit));
            }
        }
    }

    private StructDataType findStructFromSchemas(String name, ParsedDocument context) {
        var resolved = findParsedStruct(context, name);
        if (resolved == null) {
            throw new IllegalArgumentException("no struct named " + name + " in context " + context);
        }
        String structId = resolved.getOwner() + "->" + resolved.name();
        var struct = structsFromSchemas.get(structId);
        assert(struct != null);
        return struct;
    }

    private AnnotationType findAnnotationFromSchemas(String name, ParsedDocument context) {
        var resolved = findParsedAnnotation(context, name);
        String annotationId = resolved.getOwner() + "->" + resolved.name();
        var annotation = annotationsFromSchemas.get(annotationId);
        if (annotation == null) {
            throw new IllegalArgumentException("no annotation named " + name + " in context " + context);
        }
        return annotation;
    }

    private ParsedStruct findParsedStruct(ParsedDocument doc, String name) {
        ParsedStruct found = doc.getStruct(name);
        if (found != null) return found;
        for (var parent : doc.getResolvedInherits()) {
            var fromParent = findParsedStruct(parent, name);
            if (fromParent == null) continue;
            if (fromParent == found) continue;
            if (found == null) {
                found = fromParent;
            } else {
                throw new IllegalArgumentException("conflicting values for struct " + name + " in " +doc);
            }
        }
        if (found == null) {
            // TODO: be more restrictive here, but we need something
            // for imported fields. For now, fall back to looking for
            // struct in any schema.
            for (var schema : orderedInput) {
                for (var struct : schema.getDocument().getStructs()) {
                    if (struct.name().equals(name)) {
                        return struct;
                    }
                }
            }
        }
        return found;
    }

    private ParsedAnnotation findParsedAnnotation(ParsedDocument doc, String name) {
        ParsedAnnotation found = doc.getAnnotation(name);
        if (found != null) return found;
        for (var parent : doc.getResolvedInherits()) {
            var fromParent = findParsedAnnotation(parent, name);
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
        DataType nested = resolveFromContext(pType.nestedType(), context);
        return DataType.getArray(nested);
    }

    private DataType createWset(ParsedType pType, ParsedDocument context) {
        DataType nested = resolveFromContext(pType.nestedType(), context);
        boolean cine = pType.getCreateIfNonExistent();
        boolean riz = pType.getRemoveIfZero();
        return new WeightedSetDataType(nested, cine, riz);
    }

    private DataType createMap(ParsedType pType, ParsedDocument context) {
        DataType kt = resolveFromContext(pType.mapKeyType(), context);
        DataType vt = resolveFromContext(pType.mapValueType(), context);
        return DataType.getMap(kt, vt);
    }

    private DocumentType findDocFromSchemas(String name) {
        var dt = documentsFromSchemas.get(name);
        if (dt == null) {
            throw new IllegalArgumentException("missing document type for: " + name);
        }
        return dt;
    }

    private DataType createAnnRef(ParsedType pType, ParsedDocument context) {
        AnnotationType annotation = findAnnotationFromSchemas(pType.getNameOfReferencedAnnotation(), context);
        return new AnnotationReferenceDataType(annotation);
    }

    private DataType createDocRef(ParsedType pType) {
        var ref = pType.getReferencedDocumentType();
        assert(ref.getVariant() == ParsedType.Variant.DOCUMENT);
        return ReferenceDataType.createWithInferredId(findDocFromSchemas(ref.name()));
    }

    private DataType resolveFromContext(ParsedType pType, ParsedDocument context) {
        String name = pType.name();
        switch (pType.getVariant()) {
        case NONE:     return DataType.NONE;
        case BUILTIN:  return docMan.getDataType(name);
        case POSITION: return PositionDataType.INSTANCE;
        case ARRAY:    return createArray(pType, context);
        case WSET:     return createWset(pType, context);
        case MAP:      return createMap(pType, context);
        case TENSOR:   return DataType.getTensor(pType.getTensorType());
        case DOC_REFERENCE:  return createDocRef(pType);
        case ANN_REFERENCE:  return createAnnRef(pType, context);
        case DOCUMENT: return findDocFromSchemas(name);
        case STRUCT:   return findStructFromSchemas(name, context);
        case UNKNOWN:
            // fallthrough
        }
        // unknown is probably struct
        var found = findParsedStruct(context, name);
        if (found != null) {
            pType.setVariant(ParsedType.Variant.STRUCT);
            return findStructFromSchemas(name, context);
        }
        if (documentsFromSchemas.containsKey(name)) {
            pType.setVariant(ParsedType.Variant.DOCUMENT);
            return findDocFromSchemas(name);
        }
        throw new IllegalArgumentException("unknown type named '" + name + "' in context "+context);
    }

    private void registerDataTypes() {
        for (DataType t : structsFromSchemas.values()) {
            docMan.register(t);
        }
        for (DocumentType t : documentsFromSchemas.values()) {
            docMan.register(t);
        }
        for (AnnotationType t : annotationsFromSchemas.values()) {
            docMan.getAnnotationTypeRegistry().register(t);
        }
    }

    public class TypeResolver {
        private final ParsedDocument context;
        public DataType resolveType(ParsedType parsed) {
            return resolveFromContext(parsed, context);
        }
        public DataType resolveStruct(ParsedStruct parsed) {
            String structId = context.name() + "->" + parsed.name();
            var r = structsFromSchemas.get(structId);
            if (r == null) {
                throw new IllegalArgumentException("no datatype found for struct: " + structId);
            }
            return r;
        }
        TypeResolver(ParsedDocument context) {
            this.context = context;
        }
    }

    public TypeResolver makeContext(ParsedDocument doc) {
        return new TypeResolver(doc);
    }
}
