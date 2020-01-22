// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 */
public class DocumentManager {

    public DocumentmanagerConfig.Builder produce(DocumentModel model,
                                                 DocumentmanagerConfig.Builder documentConfigBuilder) {
        documentConfigBuilder.enablecompression(false);
        Set<DataType> handled = new HashSet<>();
        for(NewDocumentType documentType : model.getDocumentManager().getTypes()) {
            buildConfig(documentType, documentConfigBuilder, handled);
            buildConfig(documentType.getAnnotations(), documentConfigBuilder);
            if ( documentType != VespaDocumentType.INSTANCE) {
                DocumentmanagerConfig.Datatype.Builder dataTypeBuilder = new DocumentmanagerConfig.Datatype.Builder();
                documentConfigBuilder.datatype(dataTypeBuilder);
                buildConfig(documentType, dataTypeBuilder);
            }
        }
        return documentConfigBuilder;
    }

    private void buildConfig(DataTypeCollection type, DocumentmanagerConfig.Builder documentConfigBuilder, Set<DataType> built) {
        for (DataType dataType : type.getTypes()) {
            if (built.contains(dataType)) continue;
            built.add(dataType);
            if (dataType instanceof TemporaryStructuredDataType) continue;
            if ((dataType.getId() < 0) || (dataType.getId()> DataType.lastPredefinedDataTypeId())) {
                Datatype.Builder dataTypeBuilder = new Datatype.Builder();
                documentConfigBuilder.datatype(dataTypeBuilder);
                buildConfig(dataType, dataTypeBuilder);
            }
        }
    }

    private void buildConfig(AnnotationType type, DocumentmanagerConfig.Annotationtype.Builder atb) {
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

    private void buildConfig(Collection<AnnotationType> types, DocumentmanagerConfig.Builder builder) {
        for (AnnotationType type : types) {
            DocumentmanagerConfig.Annotationtype.Builder atb = new DocumentmanagerConfig.Annotationtype.Builder();
            buildConfig(type, atb);
            builder.annotationtype(atb);
        }
    }

    @SuppressWarnings("deprecation")
    private void buildConfig(DataType type, Datatype.Builder builder) {
        builder.id(type.getId());
        if (type instanceof ArrayDataType) {
            CollectionDataType dt = (CollectionDataType) type;
            builder.arraytype(new Datatype.Arraytype.Builder().datatype(dt.getNestedType().getId()));
        } else if (type instanceof WeightedSetDataType) {
            WeightedSetDataType dt = (WeightedSetDataType) type;
            builder.weightedsettype(new Datatype.Weightedsettype.Builder().
                    datatype(dt.getNestedType().getId()).
                    createifnonexistant(dt.createIfNonExistent()).
                    removeifzero(dt.removeIfZero()));
        } else if (type instanceof MapDataType) {
            MapDataType mtype = (MapDataType) type;
            builder.maptype(new Datatype.Maptype.Builder().
                    keytype(mtype.getKeyType().getId()).
                    valtype(mtype.getValueType().getId()));
        } else if (type instanceof DocumentType) {
            DocumentType dt = (DocumentType) type;
            Datatype.Documenttype.Builder doc = new Datatype.Documenttype.Builder();
            builder.documenttype(doc);
            doc.
                name(dt.getName()).
                headerstruct(dt.contentStruct().getId()).
                bodystruct(dt.getBodyType().getId());
            for (DocumentType inherited : dt.getInheritedTypes()) {
                doc.inherits(new Datatype.Documenttype.Inherits.Builder().name(inherited.getName()));
            }
        } else if (type instanceof NewDocumentType) {
            NewDocumentType dt = (NewDocumentType) type;
            Datatype.Documenttype.Builder doc = new Datatype.Documenttype.Builder();
            builder.documenttype(doc);
            doc.
                name(dt.getName()).
                headerstruct(dt.getHeader().getId()).
                bodystruct(dt.getBody().getId());
            for (NewDocumentType inherited : dt.getInherited()) {
                doc.inherits(new Datatype.Documenttype.Inherits.Builder().name(inherited.getName()));
            }
            buildConfig(dt.getFieldSets(), doc);
            buildImportedFieldsConfig(dt.getImportedFieldNames(), doc);
        } else if (type instanceof TemporaryStructuredDataType) {
            //Ignored
        } else if (type instanceof StructDataType) {
            StructDataType structType = (StructDataType) type;
            Datatype.Structtype.Builder structBuilder = new Datatype.Structtype.Builder();
            builder.structtype(structBuilder);
            structBuilder.name(structType.getName());
            if (structType.getCompressionConfig().type.getCode() != 0) {
                structBuilder.
                    compresstype(Datatype.Structtype.Compresstype.Enum.valueOf(structType.getCompressionConfig().type.toString())).
                    compresslevel(structType.getCompressionConfig().compressionLevel).
                    compressthreshold((int)structType.getCompressionConfig().threshold).
                    compressminsize((int)structType.getCompressionConfig().minsize);
            }
            for (com.yahoo.document.Field field : structType.getFieldsThisTypeOnly()) {
                Datatype.Structtype.Field.Builder fieldBuilder = new Datatype.Structtype.Field.Builder();
                structBuilder.field(fieldBuilder);
                fieldBuilder.name(field.getName());
                if (field.hasForcedId()) {
                    fieldBuilder.id(new Datatype.Structtype.Field.Id.Builder().id(field.getId()));
                }
                fieldBuilder.datatype(field.getDataType().getId());

                if (field.getDataType() instanceof TensorDataType)
                    fieldBuilder.detailedtype(((TensorDataType)field.getDataType()).getTensorType().toString());
            }
            for (StructDataType inherited : structType.getInheritedTypes()) {
                structBuilder.inherits(new Datatype.Structtype.Inherits.Builder().name(inherited.getName()));
            }
        } else if (type instanceof AnnotationReferenceDataType) {
            AnnotationReferenceDataType annotationRef = (AnnotationReferenceDataType) type;
            builder.annotationreftype(new Datatype.Annotationreftype.Builder().annotation(annotationRef.getAnnotationType().getName()));
        } else if (type instanceof TensorDataType) {
            // Nothing to do; the type of the tensor is instead stored in each field as detailed type information
            // to provide better compatibility. A tensor field can have its tensorType changed (in compatible ways)
            // without changing the field type and thus requiring data refeed
        } else if (type instanceof ReferenceDataType) {
            ReferenceDataType refType = (ReferenceDataType) type;
            builder.referencetype(new Datatype.Referencetype.Builder().target_type_id(refType.getTargetType().getId()));
        } else {
            throw new IllegalArgumentException("Can not create config for data type '" + type.getName());
        }
    }

    private void buildConfig(Set<FieldSet> fieldSets, Datatype.Documenttype.Builder doc) {
        for (FieldSet builtinFs : fieldSets) {
            buildConfig(builtinFs, doc);
        }
    }

    private void buildConfig(FieldSet fs, Datatype.Documenttype.Builder doc) {
        doc.fieldsets(fs.getName(), new Datatype.Documenttype.Fieldsets.Builder().fields(fs.getFieldNames()));
    }

    private void buildImportedFieldsConfig(Collection<String> fieldNames, Datatype.Documenttype.Builder builder) {
        for (String fieldName : fieldNames) {
            var ib = new DocumentmanagerConfig.Datatype.Documenttype.Importedfield.Builder();
            ib.name(fieldName);
            builder.importedfield(ib);
        }
    }

}
