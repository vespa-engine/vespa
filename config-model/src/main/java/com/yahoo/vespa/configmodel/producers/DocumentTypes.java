// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configmodel.producers;

import com.yahoo.document.*;
import com.yahoo.document.DocumenttypesConfig.Builder;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.DataTypeCollection;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.searchdefinition.FieldSets;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.vespa.documentmodel.DocumentModel;
import java.util.*;

/**
 * @author baldersheim
 */
public class DocumentTypes {

    public DocumenttypesConfig.Builder produce(DocumentModel model, DocumenttypesConfig.Builder builder) {
        Map<NewDocumentType.Name, NewDocumentType> produced = new HashMap<>();
        for(NewDocumentType documentType : model.getDocumentManager().getTypes()) {
            produceInheritOrder(documentType, builder, produced);
        }
        return builder;
    }

    private void produceInheritOrder(NewDocumentType documentType, DocumenttypesConfig.Builder builder, Map<NewDocumentType.Name, NewDocumentType> produced) {
        if ( ! produced.containsKey(documentType.getFullName())) {
            for (NewDocumentType inherited : documentType.getInherited()) {
                produceInheritOrder(inherited, builder, produced);
            }
            handle(documentType, builder);
            produced.put(documentType.getFullName(), documentType);
        }
    }

    private void handle(NewDocumentType documentType, DocumenttypesConfig.Builder builder) {
        if (documentType == VespaDocumentType.INSTANCE) {
            return;
        }
        DocumenttypesConfig.Documenttype.Builder db = new DocumenttypesConfig.Documenttype.Builder();
        db.
            id(documentType.getId()).
            name(documentType.getName()).
            headerstruct(documentType.getHeader().getId()).
            bodystruct(documentType.getBody().getId());
        Set<Integer> handled = new HashSet<>();
        for (NewDocumentType inherited : documentType.getInherited()) {
            db.inherits(new DocumenttypesConfig.Documenttype.Inherits.Builder().id(inherited.getId()));
            markAsHandled(handled, inherited.getAllTypes());
        }
        for (DataType dt : documentType.getTypes()) {
            handle(dt, db, handled);
        }
        for(AnnotationType annotation : documentType.getAnnotations()) {
            DocumenttypesConfig.Documenttype.Annotationtype.Builder atb = new DocumenttypesConfig.Documenttype.Annotationtype.Builder();
            db.annotationtype(atb);
            handle(annotation, atb);
        }
        handleFieldSets(documentType.getFieldSets(), db);
        builder.documenttype(db);
    }

    private void handleFieldSets(Set<FieldSet> fieldSets, com.yahoo.document.DocumenttypesConfig.Documenttype.Builder db) {
        for (FieldSet fs : fieldSets) {
            handleFieldSet(fs, db);
        }        
    }

    private void handleFieldSet(FieldSet fs, DocumenttypesConfig.Documenttype.Builder db) {
        db.fieldsets(fs.getName(), new DocumenttypesConfig.Documenttype.Fieldsets.Builder().fields(fs.getFieldNames()));
    }

    private void markAsHandled(Set<Integer> handled, DataTypeCollection typeCollection) {
        for (DataType type : typeCollection.getTypes()) {
            handled.add(type.getId());
        }
    }

    private void handle(AnnotationType annotation, DocumenttypesConfig.Documenttype.Annotationtype.Builder builder) {
        builder.
            id(annotation.getId()).
            name(annotation.getName());
        DataType dt = annotation.getDataType();
        if (dt!=null) {
            builder.datatype(dt.getId());
        }
        for (AnnotationType inherited : annotation.getInheritedTypes()) {
            builder.inherits(new DocumenttypesConfig.Documenttype.Annotationtype.Inherits.Builder().id(inherited.getId()));
        }
    }

    private void handle(DataType type, DocumenttypesConfig.Documenttype.Builder db, Set<Integer> handled) {
        if ((VespaDocumentType.INSTANCE.getDataType(type.getId()) == null) && ! handled.contains(type.getId())) {
            handled.add(type.getId());
            DocumenttypesConfig.Documenttype.Datatype.Builder dtb = new DocumenttypesConfig.Documenttype.Datatype.Builder();
            dtb.id(type.getId());
            if (type instanceof StructDataType) {
                dtb.type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.valueOf("STRUCT"));
                StructDataType dt = (StructDataType) type;
                DocumenttypesConfig.Documenttype.Datatype.Sstruct.Builder sb = new DocumenttypesConfig.Documenttype.Datatype.Sstruct.Builder();
                dtb.sstruct(sb);
                sb.name(dt.getName());
                if (dt.getCompressionConfig().type.getCode() != 0) {
                    sb.compression(new DocumenttypesConfig.Documenttype.Datatype.Sstruct.Compression.Builder().
                            type(DocumenttypesConfig.Documenttype.Datatype.Sstruct.Compression.Type.Enum.valueOf(dt.getCompressionConfig().type.toString())).
                            level(dt.getCompressionConfig().compressionLevel).
                            threshold((int)dt.getCompressionConfig().threshold).
                            minsize((int)dt.getCompressionConfig().minsize));
                }
                for (com.yahoo.document.Field field : dt.getFields()) {
                    sb.field(new DocumenttypesConfig.Documenttype.Datatype.Sstruct.Field.Builder().
                            name(field.getName()).
                            id(field.getId()).
                            id_v6(field.getIdV6()).
                            datatype(field.getDataType().getId()));
                    handle(field.getDataType(), db, handled);
                }
            } else if (type instanceof ArrayDataType) {
                dtb.
                    type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.valueOf("ARRAY")).
                    array(new DocumenttypesConfig.Documenttype.Datatype.Array.Builder().
                            element(new DocumenttypesConfig.Documenttype.Datatype.Array.Element.Builder().id(((ArrayDataType)type).getNestedType().getId())));
                handle(((ArrayDataType)type).getNestedType(), db, handled);
            } else if (type instanceof WeightedSetDataType) {
                dtb.type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.valueOf("WSET")).
                wset(new DocumenttypesConfig.Documenttype.Datatype.Wset.Builder().
                        key(new DocumenttypesConfig.Documenttype.Datatype.Wset.Key.Builder().
                                id(((WeightedSetDataType)type).getNestedType().getId())).
                        createifnonexistent(((WeightedSetDataType)type).createIfNonExistent()).
                        removeifzero(((WeightedSetDataType)type).removeIfZero()));
                handle(((WeightedSetDataType)type).getNestedType(), db, handled);
            } else if (type instanceof MapDataType) {
                dtb.
                    type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.valueOf("MAP")).
                    map(new DocumenttypesConfig.Documenttype.Datatype.Map.Builder().
                        key(new DocumenttypesConfig.Documenttype.Datatype.Map.Key.Builder().
                            id(((MapDataType)type).getKeyType().getId())).
                        value(new DocumenttypesConfig.Documenttype.Datatype.Map.Value.Builder().
                            id(((MapDataType)type).getValueType().getId())));
                handle(((MapDataType)type).getKeyType(), db, handled);
                handle(((MapDataType)type).getValueType(), db, handled);
            } else if (type instanceof AnnotationReferenceDataType) {
                dtb.
                    type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.valueOf("ANNOTATIONREF")).
                    annotationref(new DocumenttypesConfig.Documenttype.Datatype.Annotationref.Builder().
                            annotation(new DocumenttypesConfig.Documenttype.Datatype.Annotationref.Annotation.Builder().
                                    id(((AnnotationReferenceDataType)type).getAnnotationType().getId())));
            } else {
                return;
            }
            db.datatype(dtb);
        }
    }
}

