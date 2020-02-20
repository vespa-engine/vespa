// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configmodel.producers;

import com.yahoo.document.*;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.DataTypeCollection;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.vespa.documentmodel.DocumentModel;
import java.util.*;

/**
 * @author baldersheim
 */
public class DocumentTypes {

    public DocumenttypesConfig.Builder produce(DocumentModel model, DocumenttypesConfig.Builder builder) {
        Map<NewDocumentType.Name, NewDocumentType> produced = new HashMap<>();
        for (NewDocumentType documentType : model.getDocumentManager().getTypes()) {
            produceInheritOrder(documentType, builder, produced);
        }
        return builder;
    }

    private void produceInheritOrder(NewDocumentType documentType, DocumenttypesConfig.Builder builder, Map<NewDocumentType.Name, NewDocumentType> produced) {
        if (!produced.containsKey(documentType.getFullName())) {
            for (NewDocumentType inherited : documentType.getInherited()) {
                produceInheritOrder(inherited, builder, produced);
            }
            buildConfig(documentType, builder);
            produced.put(documentType.getFullName(), documentType);
        }
    }

    private void buildConfig(NewDocumentType documentType, DocumenttypesConfig.Builder builder) {
        if (documentType == VespaDocumentType.INSTANCE) {
            return;
        }
        DocumenttypesConfig.Documenttype.Builder db = new DocumenttypesConfig.Documenttype.Builder();
        db.
                id(documentType.getId()).
                name(documentType.getName()).
                headerstruct(documentType.getHeader().getId()).
                bodystruct(documentType.getBody().getId());
        Set<Integer> built = new HashSet<>();
        for (NewDocumentType inherited : documentType.getInherited()) {
            db.inherits(new DocumenttypesConfig.Documenttype.Inherits.Builder().id(inherited.getId()));
            markAsBuilt(built, inherited.getAllTypes());
        }
        for (DataType dt : documentType.getTypes()) {
            buildConfig(dt, db, built);
        }
        for (AnnotationType annotation : documentType.getAnnotations()) {
            DocumenttypesConfig.Documenttype.Annotationtype.Builder atb = new DocumenttypesConfig.Documenttype.Annotationtype.Builder();
            db.annotationtype(atb);
            buildConfig(annotation, atb);
        }
        buildConfig(documentType.getFieldSets(), db);
        buildImportedFieldsConfig(documentType.getImportedFieldNames(), db);
        builder.documenttype(db);
    }

    private void buildConfig(Set<FieldSet> fieldSets, com.yahoo.document.DocumenttypesConfig.Documenttype.Builder db) {
        for (FieldSet fs : fieldSets) {
            buildConfig(fs, db);
        }
    }

    private void buildConfig(FieldSet fs, DocumenttypesConfig.Documenttype.Builder db) {
        db.fieldsets(fs.getName(), new DocumenttypesConfig.Documenttype.Fieldsets.Builder().fields(fs.getFieldNames()));
    }

    private void markAsBuilt(Set<Integer> built, DataTypeCollection typeCollection) {
        for (DataType type : typeCollection.getTypes()) {
            built.add(type.getId());
        }
    }

    private void buildConfig(AnnotationType annotation, DocumenttypesConfig.Documenttype.Annotationtype.Builder builder) {
        builder.
                id(annotation.getId()).
                name(annotation.getName());
        DataType dt = annotation.getDataType();
        if (dt != null) {
            builder.datatype(dt.getId());
        }
        for (AnnotationType inherited : annotation.getInheritedTypes()) {
            builder.inherits(new DocumenttypesConfig.Documenttype.Annotationtype.Inherits.Builder().id(inherited.getId()));
        }
    }

    private void buildConfig(DataType type, DocumenttypesConfig.Documenttype.Builder documentBuilder, Set<Integer> built) {
        if ((VespaDocumentType.INSTANCE.getDataType(type.getId()) == null) && !built.contains(type.getId())) {
            built.add(type.getId());
            DocumenttypesConfig.Documenttype.Datatype.Builder dataTypeBuilder = new DocumenttypesConfig.Documenttype.Datatype.Builder();
            dataTypeBuilder.id(type.getId());
            if (type instanceof StructDataType) {
                buildConfig((StructDataType) type, dataTypeBuilder, documentBuilder, built);
            } else if (type instanceof ArrayDataType) {
                buildConfig((ArrayDataType) type, dataTypeBuilder, documentBuilder, built);
            } else if (type instanceof WeightedSetDataType) {
                buildConfig((WeightedSetDataType) type, dataTypeBuilder, documentBuilder, built);
            } else if (type instanceof MapDataType) {
                buildConfig((MapDataType) type, dataTypeBuilder, documentBuilder, built);
            } else if (type instanceof AnnotationReferenceDataType) {
                buildConfig((AnnotationReferenceDataType) type, dataTypeBuilder);
            } else if (type instanceof TensorDataType) {
                // The type of the tensor is not stored here but instead in each field as detailed type information
                // to provide better compatibility. A tensor field can have its tensorType changed (in compatible ways)
                // without changing the field type and thus requiring data refeed
                return;
            } else if (type instanceof ReferenceDataType) {
                buildConfig((ReferenceDataType) type, documentBuilder);
                return;
            } else {
                return;
            }
            documentBuilder.datatype(dataTypeBuilder);
        }
    }

    private void buildImportedFieldsConfig(Collection<String> fieldNames, DocumenttypesConfig.Documenttype.Builder  builder) {
        for (String fieldName : fieldNames) {
            var ib = new DocumenttypesConfig.Documenttype.Importedfield.Builder();
            ib.name(fieldName);
            builder.importedfield(ib);
        }
    }

    private void buildConfig(StructDataType type,
                             DocumenttypesConfig.Documenttype.Datatype.Builder dataTypeBuilder,
                             DocumenttypesConfig.Documenttype.Builder documentBuilder,
                             Set<Integer> built) {
        dataTypeBuilder.type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.STRUCT);
        DocumenttypesConfig.Documenttype.Datatype.Sstruct.Builder structBuilder = new DocumenttypesConfig.Documenttype.Datatype.Sstruct.Builder();
        dataTypeBuilder.sstruct(structBuilder);
        structBuilder.name(type.getName());
        if (type.getCompressionConfig().type.getCode() != 0) {
            structBuilder.compression(new DocumenttypesConfig.Documenttype.Datatype.Sstruct.Compression.Builder().
                    type(DocumenttypesConfig.Documenttype.Datatype.Sstruct.Compression.Type.Enum.valueOf(type.getCompressionConfig().type.toString())).
                    level(type.getCompressionConfig().compressionLevel).
                    threshold((int) type.getCompressionConfig().threshold).
                    minsize((int) type.getCompressionConfig().minsize));
        }
        for (com.yahoo.document.Field field : type.getFields()) {
            DocumenttypesConfig.Documenttype.Datatype.Sstruct.Field.Builder builder =
                    new DocumenttypesConfig.Documenttype.Datatype.Sstruct.Field.Builder();
            builder.name(field.getName()).
                    id(field.getId()).
                    datatype(field.getDataType().getId());
            if (field.getDataType() instanceof TensorDataType) {
                builder.detailedtype(((TensorDataType) field.getDataType()).getTensorType().toString());
            }
            structBuilder.field(builder);
            buildConfig(field.getDataType(), documentBuilder, built);
        }
    }

    private void buildConfig(ArrayDataType type,
                             DocumenttypesConfig.Documenttype.Datatype.Builder dataTypeBuilder,
                             DocumenttypesConfig.Documenttype.Builder documentBuilder,
                             Set<Integer> built) {
        dataTypeBuilder.
                type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.ARRAY).
                array(new DocumenttypesConfig.Documenttype.Datatype.Array.Builder().
                        element(new DocumenttypesConfig.Documenttype.Datatype.Array.Element.Builder().id(type.getNestedType().getId())));
        buildConfig(type.getNestedType(), documentBuilder, built);
    }

    private void buildConfig(WeightedSetDataType type,
                             DocumenttypesConfig.Documenttype.Datatype.Builder dataTypeBuilder,
                             DocumenttypesConfig.Documenttype.Builder documentBuilder,
                             Set<Integer> built) {
        dataTypeBuilder.type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.WSET).
                wset(new DocumenttypesConfig.Documenttype.Datatype.Wset.Builder().
                        key(new DocumenttypesConfig.Documenttype.Datatype.Wset.Key.Builder().
                                id(type.getNestedType().getId())).
                        createifnonexistent(type.createIfNonExistent()).
                        removeifzero(type.removeIfZero()));
        buildConfig(type.getNestedType(), documentBuilder, built);
    }

    private void buildConfig(MapDataType type,
                             DocumenttypesConfig.Documenttype.Datatype.Builder dataTypeBuilder,
                             DocumenttypesConfig.Documenttype.Builder documentBuilder,
                             Set<Integer> built) {
        dataTypeBuilder.
                type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.MAP).
                map(new DocumenttypesConfig.Documenttype.Datatype.Map.Builder().
                        key(new DocumenttypesConfig.Documenttype.Datatype.Map.Key.Builder().
                                id(type.getKeyType().getId())).
                        value(new DocumenttypesConfig.Documenttype.Datatype.Map.Value.Builder().
                                id(type.getValueType().getId())));
        buildConfig(type.getKeyType(), documentBuilder, built);
        buildConfig(type.getValueType(), documentBuilder, built);
    }

    private void buildConfig(AnnotationReferenceDataType type,
                             DocumenttypesConfig.Documenttype.Datatype.Builder dataTypeBuilder) {
        dataTypeBuilder.
                type(DocumenttypesConfig.Documenttype.Datatype.Type.Enum.ANNOTATIONREF).
                annotationref(new DocumenttypesConfig.Documenttype.Datatype.Annotationref.Builder().
                        annotation(new DocumenttypesConfig.Documenttype.Datatype.Annotationref.Annotation.Builder().
                                id(type.getAnnotationType().getId())));
    }

    private void buildConfig(ReferenceDataType type,
                             DocumenttypesConfig.Documenttype.Builder documentBuilder) {
        ReferenceDataType refType = type;
        DocumenttypesConfig.Documenttype.Referencetype.Builder refBuilder =
                new DocumenttypesConfig.Documenttype.Referencetype.Builder();
        refBuilder.id(refType.getId());
        refBuilder.target_type_id(type.getTargetType().getId());
        documentBuilder.referencetype(refBuilder);
    }

}
