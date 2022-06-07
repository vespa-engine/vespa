// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.OwnedStructDataType;
import com.yahoo.schema.document.annotation.SDAnnotationType;

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

    private final Map<String, DocumentType> documentsFromSchemas = new HashMap<>();
    private final Map<String, StructDataType> structsFromSchemas = new HashMap<>();
    private final Map<String, SDAnnotationType> annotationsFromSchemas = new HashMap<>();

    private void startDataTypes() {
        for (var schema : orderedInput) {
            String name = schema.getDocument().name();
            documentsFromSchemas.put(name, new DocumentType(name));
        }
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var struct : doc.getStructs()) {
                String structId = doc.name() + "->" + struct.name();
                var dt = new OwnedStructDataType(struct.name(), doc.name());
                structsFromSchemas.put(structId, dt);
            }
            for (var annotation : doc.getAnnotations()) {
                String annId = doc.name() + "->" + annotation.name();
                var at = new SDAnnotationType(annotation.name());
                annotationsFromSchemas.put(annId, at);
                for (String inherit : annotation.getInherited()) {
                    at.inherit(inherit);
                }
                var withStruct = annotation.getStruct();
                if (withStruct.isPresent()) {
                    ParsedStruct struct = withStruct.get();
                    String structId = doc.name() + "->" + struct.name();
                    var old = structsFromSchemas.put(structId, new OwnedStructDataType(struct.name(), doc.name()));
                    assert(old == null);
                }
            }
        }
    }

    void fillAnnotationStruct(ParsedAnnotation annotation) {
        var withStruct = annotation.getStruct();
        if (withStruct.isPresent()) {
            var doc = annotation.getOwnerDoc();
            var toFill = findStructFromParsed(withStruct.get());
            for (ParsedField field : withStruct.get().getFields()) {
                var t = resolveFromContext(field.getType(), doc);
                var f = field.hasIdOverride()
                    ? new com.yahoo.document.Field(field.name(), field.idOverride(), t)
                    : new com.yahoo.document.Field(field.name(), t);
                toFill.addField(f);
            }
            for (var parent : annotation.getResolvedInherits()) {
                parent.getStruct().ifPresent
                    (ps -> toFill.inherit(findStructFromParsed(ps)));
            }
            var at = findAnnotationFromParsed(annotation);
            at.setDataType(toFill);
        }
    }

    private void fillDataTypes() {
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var annotation : doc.getAnnotations()) {
                var at = findAnnotationFromParsed(annotation);
                for (var parent : annotation.getResolvedInherits()) {
                    at.inherit(findAnnotationFromParsed(parent));
                }
                fillAnnotationStruct(annotation);
            }
            for (var struct : doc.getStructs()) {
                var toFill = findStructFromParsed(struct);
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
                for (var inherit : struct.getResolvedInherits()) {
                    var parent = findStructFromParsed(inherit);
                    // ensure a nice, compatible exception message
                    for (var field : toFill.getFields()) {
                        if (parent.hasField(field)) {
                            for (var base : parent.getInheritedTypes()) {
                                if (base.hasField(field)) {
                                    parent = base;
                                }
                            }
                            throw new IllegalArgumentException
                                ("In document " + doc.name() + ": struct " + struct.name() +
                                 " cannot inherit from " + parent.getName() + " and redeclare field " + field.getName());
                        }
                    }
                    toFill.inherit(parent);
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
                if (docToFill.hasField(name)) continue;
                var t = resolveFromContext(extraField.getType(), doc);
                var f = new com.yahoo.document.Field(name, t);
                docToFill.addField(f);
            }
            for (var fieldset : schema.getFieldSets()) {
                fieldSets.put(fieldset.name(), fieldset.getFieldNames());
            }
            for (String inherit : doc.getInherited()) {
                docToFill.inherit(findDocFromSchemas(inherit));
            }
            docToFill.addFieldSets(fieldSets);
        }
    }

    private StructDataType findStructFromParsed(ParsedStruct resolved) {
        String structId = resolved.getOwnerName() + "->" + resolved.name();
        var struct = structsFromSchemas.get(structId);
        assert(struct != null);
        return struct;
    }

    private StructDataType findStructFromSchemas(String name, ParsedDocument context) {
        var resolved = context.findParsedStruct(name);
        if (resolved == null) {
            throw new IllegalArgumentException("no struct named " + name + " in context " + context);
        }
        return findStructFromParsed(resolved);
    }

    private SDAnnotationType findAnnotationFromSchemas(String name, ParsedDocument context) {
        var resolved = context.findParsedAnnotation(name);
        String annotationId = resolved.getOwnerName() + "->" + resolved.name();
        var annotation = annotationsFromSchemas.get(annotationId);
        if (annotation == null) {
            throw new IllegalArgumentException("no annotation named " + name + " in context " + context);
        }
        return annotation;
    }

    private SDAnnotationType findAnnotationFromParsed(ParsedAnnotation resolved) {
        String annotationId = resolved.getOwnerName() + "->" + resolved.name();
        var annotation = annotationsFromSchemas.get(annotationId);
        if (annotation == null) {
            throw new IllegalArgumentException("no annotation " + resolved.name() + " in " + resolved.getOwnerName());
        }
        return annotation;
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
        SDAnnotationType annotation = findAnnotationFromSchemas(pType.getNameOfReferencedAnnotation(), context);
        return new AnnotationReferenceDataType(annotation);
    }

    private DataType createDocRef(ParsedType pType) {
        var ref = pType.getReferencedDocumentType();
        assert(ref.getVariant() == ParsedType.Variant.DOCUMENT);
        return new NewDocumentReferenceDataType(findDocFromSchemas(ref.name()));
    }

    private DataType getBuiltinType(String name) {
        switch (name) {
        case "bool":      return DataType.BOOL;
        case "byte":      return DataType.BYTE;
        case "int":       return DataType.INT;
        case "long":      return DataType.LONG;
        case "string":    return DataType.STRING;
        case "float":     return DataType.FLOAT;
        case "double":    return DataType.DOUBLE;
        case "uri":       return DataType.URI;
        case "predicate": return DataType.PREDICATE;
        case "raw":       return DataType.RAW;
        case "tag":       return DataType.TAG;
        case "float16":   return DataType.FLOAT16;
        default:
            throw new IllegalArgumentException("Unknown builtin type: "+name);
        }
    }

    private DataType resolveFromContext(ParsedType pType, ParsedDocument context) {
        String name = pType.name();
        switch (pType.getVariant()) {
        case NONE:     return DataType.NONE;
        case BUILTIN:  return getBuiltinType(name);
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
        var found = context.findParsedStruct(name);
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
            docMan.registerDocumentType(t);
        }
        for (SDAnnotationType t : annotationsFromSchemas.values()) {
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
        public SDAnnotationType resolveAnnotation(String name) {
            return findAnnotationFromSchemas(name, context);
        }
        TypeResolver(ParsedDocument context) {
            this.context = context;
        }
    }

    public TypeResolver makeContext(ParsedDocument doc) {
        return new TypeResolver(doc);
    }
}
