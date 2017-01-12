// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configmodel.producers;

import com.yahoo.document.config.DocumentmanagerConfig;
import static com.yahoo.document.config.DocumentmanagerConfig.*;
import com.yahoo.document.*;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.DataTypeCollection;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.vespa.documentmodel.DocumentModel;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author    baldersheim
 * @since     2010-02-19
 */
public class DocumentManager {

    public DocumentmanagerConfig.Builder produce(DocumentModel model, DocumentmanagerConfig.Builder docman) {
        docman.enablecompression(false);
        Set<DataType> handled = new HashSet<>();
        for(NewDocumentType documentType : model.getDocumentManager().getTypes()) {
            handle(documentType, docman, handled);
            handleAnnotations(documentType.getAnnotations(), docman);
            if ( documentType != VespaDocumentType.INSTANCE) {
                DocumentmanagerConfig.Datatype.Builder dt = new DocumentmanagerConfig.Datatype.Builder();
                docman.datatype(dt);
                handleDataType(documentType, dt);
            }
        }
        return docman;
    }

    private void handle(DataTypeCollection type, DocumentmanagerConfig.Builder docman, Set<DataType> handled) {
        for (DataType dataType : type.getTypes()) {
            if (handled.contains(dataType)) continue;
            handled.add(dataType);
            if (dataType instanceof TemporaryStructuredDataType) continue;
            if ((dataType.getId() < 0) || (DataType.lastPredefinedDataTypeId() < dataType.getId())) {
                Datatype.Builder dtc = new Datatype.Builder();
                docman.datatype(dtc);
                handleDataType(dataType, dtc);
            }
        }
    }

    private void handleAnnotation(AnnotationType type, DocumentmanagerConfig.Annotationtype.Builder atb) {
        atb.
            id(type.getId()).
            name(type.getName());
        if (type.getDataType() != null) {
            atb.datatype(type.getDataType().getId());
        }
        if ( ! type.getInheritedTypes().isEmpty()) {
            for (AnnotationType inherited : type.getInheritedTypes()) {
                atb.inherits(new DocumentmanagerConfig.Annotationtype.Inherits.Builder().id(inherited.getId()));
            }
        }
    }
    private void handleAnnotations(Collection<AnnotationType> types, DocumentmanagerConfig.Builder builder) {
        for (AnnotationType type : types) {
            DocumentmanagerConfig.Annotationtype.Builder atb = new DocumentmanagerConfig.Annotationtype.Builder();
            handleAnnotation(type, atb);
            builder.annotationtype(atb);
        }
    }

    private void handleDataType(DataType type, Datatype.Builder dtc) {
        dtc.id(type.getId());
        if (type instanceof ArrayDataType) {
            CollectionDataType dt = (CollectionDataType) type;
            dtc.arraytype(new Datatype.Arraytype.Builder().datatype(dt.getNestedType().getId()));
        } else if (type instanceof WeightedSetDataType) {
            WeightedSetDataType dt = (WeightedSetDataType) type;
            dtc.weightedsettype(new Datatype.Weightedsettype.Builder().
                    datatype(dt.getNestedType().getId()).
                    createifnonexistant(dt.createIfNonExistent()).
                    removeifzero(dt.removeIfZero()));
        } else if (type instanceof MapDataType) {
            MapDataType mtype = (MapDataType) type;
            dtc.maptype(new Datatype.Maptype.Builder().
                    keytype(mtype.getKeyType().getId()).
                    valtype(mtype.getValueType().getId()));
        } else if (type instanceof DocumentType) {
            DocumentType dt = (DocumentType) type;
            Datatype.Documenttype.Builder doc = new Datatype.Documenttype.Builder();
            dtc.documenttype(doc);
            doc.
                name(dt.getName()).
                headerstruct(dt.getHeaderType().getId()).
                bodystruct(dt.getBodyType().getId());
            for (DocumentType inherited : dt.getInheritedTypes()) {
                doc.inherits(new Datatype.Documenttype.Inherits.Builder().name(inherited.getName()));
            }
        } else if (type instanceof NewDocumentType) {
            NewDocumentType dt = (NewDocumentType) type;
            Datatype.Documenttype.Builder doc = new Datatype.Documenttype.Builder();
            dtc.documenttype(doc);
            doc.
                name(dt.getName()).
                headerstruct(dt.getHeader().getId()).
                bodystruct(dt.getBody().getId());
            for (NewDocumentType inherited : dt.getInherited()) {
                doc.inherits(new Datatype.Documenttype.Inherits.Builder().name(inherited.getName()));
            }
            handleFieldSets(dt.getFieldSets(), doc);
        } else if (type instanceof TemporaryStructuredDataType) {
            //Ignored
        } else if (type instanceof StructDataType) {
            StructDataType dt = (StructDataType) type;
            Datatype.Structtype.Builder st = new Datatype.Structtype.Builder();
            dtc.structtype(st);
            st.name(dt.getName());
            if (dt.getCompressionConfig().type.getCode() != 0) {
                st.
                    compresstype(Datatype.Structtype.Compresstype.Enum.valueOf(dt.getCompressionConfig().type.toString())).
                    compresslevel(dt.getCompressionConfig().compressionLevel).
                    compressthreshold((int)dt.getCompressionConfig().threshold).
                    compressminsize((int)dt.getCompressionConfig().minsize);
            }
            for (com.yahoo.document.Field field : dt.getFieldsThisTypeOnly()) {
                Datatype.Structtype.Field.Builder fb = new Datatype.Structtype.Field.Builder();
                st.field(fb);
                fb.name(field.getName());
                if (field.hasForcedId()) {
                    fb.id(new Datatype.Structtype.Field.Id.Builder().id(field.getId()));
                }
                fb.datatype(field.getDataType().getId());
            }
            for (StructDataType inherited : dt.getInheritedTypes()) {
                st.inherits(new Datatype.Structtype.Inherits.Builder().name(inherited.getName()));
            }
        } else if (type instanceof AnnotationReferenceDataType) {
            AnnotationReferenceDataType annotationRef = (AnnotationReferenceDataType) type;
            dtc.annotationreftype(new Datatype.Annotationreftype.Builder().annotation(annotationRef.getAnnotationType().getName()));
        } else {
            throw new IllegalArgumentException("Can not handle datatype '" + type.getName());
        }
    }

    private void handleFieldSets(Set<FieldSet> fieldSets, Datatype.Documenttype.Builder doc) {

        for (FieldSet builtinFs : fieldSets) {
            handleFieldSet(builtinFs, doc);
        }
    }

    private void handleFieldSet(FieldSet fs, Datatype.Documenttype.Builder doc) {
        doc.fieldsets(fs.getName(), new Datatype.Documenttype.Fieldsets.Builder().fields(fs.getFieldNames()));
    }
}
