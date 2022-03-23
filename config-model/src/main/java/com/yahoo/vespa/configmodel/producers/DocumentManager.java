// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configmodel.producers;

import com.yahoo.document.config.DocumentmanagerConfig;
import static com.yahoo.document.config.DocumentmanagerConfig.*;
import com.yahoo.document.*;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.DataTypeCollection;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.OwnedTemporaryType;
import com.yahoo.documentmodel.TemporaryUnknownType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.vespa.documentmodel.DocumentModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author baldersheim
 * @author arnej
 */
public class DocumentManager {

    private boolean useV8GeoPositions = false;
    private boolean useV8DocManagerCfg = true;

    public DocumentManager useV8GeoPositions(boolean value) {
        this.useV8GeoPositions = value;
        return this;
    }
    public DocumentManager useV8DocManagerCfg(boolean value) {
        this.useV8DocManagerCfg = value;
        return this;
    }

    public DocumentmanagerConfig.Builder produce(DocumentModel model,
                                                 DocumentmanagerConfig.Builder documentConfigBuilder)
    {
        if (useV8DocManagerCfg) {
            return produceDocTypes(model, documentConfigBuilder);
        } else {
            return produceDataTypes(model, documentConfigBuilder);
        }
    }   

    public DocumentmanagerConfig.Builder produceDataTypes(DocumentModel model,
                                                          DocumentmanagerConfig.Builder documentConfigBuilder)
    {
        documentConfigBuilder.enablecompression(false);
        documentConfigBuilder.usev8geopositions(this.useV8GeoPositions);
        Set<DataType> handled = new HashSet<>();
        for(NewDocumentType documentType : model.getDocumentManager().getTypes()) {
            buildConfig(documentType, documentConfigBuilder, handled);
            buildConfig(documentType.getAnnotations(), documentConfigBuilder);
            if (documentType != VespaDocumentType.INSTANCE && ! handled.contains(documentType)) {
                handled.add(documentType);
                DocumentmanagerConfig.Datatype.Builder dataTypeBuilder = new DocumentmanagerConfig.Datatype.Builder();
                documentConfigBuilder.datatype(dataTypeBuilder);
                buildConfig(documentType, dataTypeBuilder);
            }
        }
        return documentConfigBuilder;
    }

    @SuppressWarnings("deprecation")
    private void buildConfig(DataTypeCollection type, DocumentmanagerConfig.Builder documentConfigBuilder, Set<DataType> built) {
        List<DataType> todo = new ArrayList<>(type.getTypes());
        Collections.sort(todo, (a, b) -> (a.getName().equals(b.getName())
                                          ? a.getId() - b.getId()
                                          : a.getName().compareTo(b.getName())));
        for (DataType dataType : todo) {
            if (built.contains(dataType)) continue;
            built.add(dataType);
            if (dataType instanceof TemporaryStructuredDataType) {
                throw new IllegalArgumentException("Can not create config for temporary data type: " + dataType.getName());
            }
            if (dataType instanceof TemporaryUnknownType) {
                throw new IllegalArgumentException("Can not create config for temporary data type: " + dataType.getName());
            }
            if (dataType instanceof OwnedTemporaryType) {
                throw new IllegalArgumentException("Can not create config for temporary data type: " + dataType.getName());
            }
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
            throw new IllegalArgumentException("Can not create config for unadorned document type: " + type.getName());
        } else if (type instanceof NewDocumentType) {
            NewDocumentType dt = (NewDocumentType) type;
            Datatype.Documenttype.Builder doc = new Datatype.Documenttype.Builder();
            builder.documenttype(doc);
            doc.
                name(dt.getName()).
                headerstruct(dt.getContentStruct().getId());
            for (NewDocumentType inherited : dt.getInherited()) {
                doc.inherits(new Datatype.Documenttype.Inherits.Builder().name(inherited.getName()));
            }
            buildConfig(dt.getFieldSets(), doc);
            buildImportedFieldsConfig(dt.getImportedFieldNames(), doc);
        } else if (type instanceof StructDataType) {
            StructDataType structType = (StructDataType) type;
            Datatype.Structtype.Builder structBuilder = new Datatype.Structtype.Builder();
            builder.structtype(structBuilder);
            structBuilder.name(structType.getName());
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
        } else if (type instanceof NewDocumentReferenceDataType) {
            NewDocumentReferenceDataType refType = (NewDocumentReferenceDataType) type;
            if (refType.isTemporary()) {
                throw new IllegalArgumentException("Still temporary: " + refType);
            }
            builder.referencetype(new Datatype.Referencetype.Builder().target_type_id(refType.getTargetTypeId()));
        } else {
            throw new IllegalArgumentException("Can not create config for data type " + type + " of class " + type.getClass());
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


    // Alternate (new) way to build config:

    public DocumentmanagerConfig.Builder produceDocTypes(DocumentModel model, DocumentmanagerConfig.Builder builder) {
        builder.usev8geopositions(this.useV8GeoPositions);
        Map<NewDocumentType.Name, NewDocumentType> produced = new HashMap<>();
        var indexMap = new IdxMap();
        for (NewDocumentType documentType : model.getDocumentManager().getTypes()) {
            docTypeInheritOrder(documentType, builder, produced, indexMap);
        }
        indexMap.verifyAllDone();
        return builder;
    }

    private void docTypeInheritOrder(NewDocumentType documentType,
                                     DocumentmanagerConfig.Builder builder,
                                     Map<NewDocumentType.Name, NewDocumentType> produced,
                                     IdxMap indexMap)
    {
        if (! produced.containsKey(documentType.getFullName())) {
            for (NewDocumentType inherited : documentType.getInherited()) {
                docTypeInheritOrder(inherited, builder, produced, indexMap);
            }
            docTypeBuild(documentType, builder, indexMap);
            produced.put(documentType.getFullName(), documentType);
        }
    }

    static private class IdxMap {
        private Map<Integer, Boolean> doneMap = new HashMap<>();
        private Map<Object, Integer> map = new IdentityHashMap<>();
        void add(Object someType) {
            assert(someType != null);
            // the adding of "10000" here is mostly to make it more
            // unique to grep for when debugging
            int nextIdx = 10000 + map.size();
            map.computeIfAbsent(someType, k -> nextIdx);
        }
        int idxOf(Object someType) {
            if (someType instanceof DocumentType) {
                var dt = (DocumentType) someType;
                if (dt.getId() == 8) {
                    return idxOf(VespaDocumentType.INSTANCE);
                }
            }
            add(someType);
            return map.get(someType);
        }
        boolean isDone(Object someType) {
            return doneMap.computeIfAbsent(idxOf(someType), k -> false);
        }
        void setDone(Object someType) {
            assert(! isDone(someType));
            doneMap.put(idxOf(someType), true);
        }
        void verifyAllDone() {
            for (var entry : map.entrySet()) {
                Object needed = entry.getKey();
                if (! isDone(needed)) {
                    throw new IllegalArgumentException("Could not generate config for all needed types, missing: " +
                                                       needed + " of class " + needed.getClass());
                }
            }
        }
    }

    static private <T> List<T> sortedList(Collection<T> unsorted, Comparator<T> cmp) {
        var list = new ArrayList<T>();
        list.addAll(unsorted);
        list.sort(cmp);
        return list;
    }

    private void docTypeBuild(NewDocumentType documentType, DocumentmanagerConfig.Builder builder, IdxMap indexMap) {
        DocumentmanagerConfig.Doctype.Builder db = new DocumentmanagerConfig.Doctype.Builder();
        db.
            idx(indexMap.idxOf(documentType)).
            name(documentType.getName()).
            contentstruct(indexMap.idxOf(documentType.getContentStruct()));
        docTypeBuildFieldSets(documentType.getFieldSets(), db);
        docTypeBuildImportedFields(documentType.getImportedFieldNames(), db);
        for (NewDocumentType inherited : documentType.getInherited()) {
            db.inherits(b -> b.idx(indexMap.idxOf(inherited)));
        }
        docTypeBuildAnyType(documentType.getContentStruct(), db, indexMap);

        for (DataType dt : sortedList(documentType.getAllTypes().getTypes(),
                                      (a,b) -> a.getName().compareTo(b.getName()))) {
            docTypeBuildAnyType(dt, db, indexMap);
        }
        for (AnnotationType ann : sortedList(documentType.getAnnotations(),
                                             (a,b) -> a.getName().compareTo(b.getName()))) {
            docTypeBuildAnnotationType(ann, db, indexMap);
        }
        builder.doctype(db);
        indexMap.setDone(documentType);
    }

    private void docTypeBuildFieldSets(Set<FieldSet> fieldSets, DocumentmanagerConfig.Doctype.Builder db) {
        for (FieldSet fs : fieldSets) {
            docTypeBuildOneFieldSet(fs, db);
        }
    }

    private void docTypeBuildOneFieldSet(FieldSet fs, DocumentmanagerConfig.Doctype.Builder db) {
        db.fieldsets(fs.getName(), new DocumentmanagerConfig.Doctype.Fieldsets.Builder().fields(fs.getFieldNames()));
    }

    private void docTypeBuildAnnotationType(AnnotationType annotation, DocumentmanagerConfig.Doctype.Builder builder, IdxMap indexMap) {
        if (indexMap.isDone(annotation)) {
            return;
        }
        indexMap.setDone(annotation);
        var annBuilder = new DocumentmanagerConfig.Doctype.Annotationtype.Builder();
        annBuilder
            .idx(indexMap.idxOf(annotation))
            .name(annotation.getName())
            .internalid(annotation.getId());
        DataType nested = annotation.getDataType();
        if (nested != null) {
            annBuilder.datatype(indexMap.idxOf(nested));
            docTypeBuildAnyType(nested, builder, indexMap);
        }
        for (AnnotationType inherited : annotation.getInheritedTypes()) {
            annBuilder.inherits(inhBuilder -> inhBuilder.idx(indexMap.idxOf(inherited)));

        }
        builder.annotationtype(annBuilder);
    }

    @SuppressWarnings("deprecation")
    private void docTypeBuildAnyType(DataType type, DocumentmanagerConfig.Doctype.Builder documentBuilder, IdxMap indexMap) {
        if (indexMap.isDone(type)) {
            return;
        }
        if (type instanceof NewDocumentType) {
            // should be in the top-level list and handled there
            return;
        }
        if ((type instanceof DocumentType) && (type.getId() == 8)) {
            // special handling
            return;
        }
        indexMap.setDone(type);
        if (type instanceof TemporaryStructuredDataType) {
            throw new IllegalArgumentException("Can not create config for temporary data type: " + type.getName());
        } else if (type instanceof TemporaryUnknownType) {
            throw new IllegalArgumentException("Can not create config for temporary data type: " + type.getName());
        } else if (type instanceof OwnedTemporaryType) {
            throw new IllegalArgumentException("Can not create config for temporary data type: " + type.getName());
        } else if (type instanceof StructDataType) {
            docTypeBuildOneType((StructDataType) type, documentBuilder, indexMap);
        } else if (type instanceof ArrayDataType) {
            docTypeBuildOneType((ArrayDataType) type, documentBuilder, indexMap);
        } else if (type instanceof WeightedSetDataType) {
            docTypeBuildOneType((WeightedSetDataType) type, documentBuilder, indexMap);
        } else if (type instanceof MapDataType) {
            docTypeBuildOneType((MapDataType) type, documentBuilder, indexMap);
        } else if (type instanceof AnnotationReferenceDataType) {
            docTypeBuildOneType((AnnotationReferenceDataType) type, documentBuilder, indexMap);
        } else if (type instanceof TensorDataType) {
            docTypeBuildOneType((TensorDataType) type, documentBuilder, indexMap);
        } else if (type instanceof NewDocumentReferenceDataType) {
            var refType = (NewDocumentReferenceDataType) type;
            if (refType.isTemporary()) {
                throw new IllegalArgumentException("Still temporary: " + refType);
            }
            docTypeBuildOneType(refType, documentBuilder, indexMap);
        } else if (type instanceof PrimitiveDataType) {
            docTypeBuildOneType((PrimitiveDataType) type, documentBuilder, indexMap);
        } else if (type instanceof DocumentType) {
            throw new IllegalArgumentException("Can not create config for unadorned document type: " + type.getName() + " id "+type.getId());
        } else {
            throw new IllegalArgumentException("Can not create config for data type " + type + " of class " + type.getClass());
        }
    }

    private void docTypeBuildImportedFields(Collection<String> fieldNames, DocumentmanagerConfig.Doctype.Builder builder) {
        for (String fieldName : fieldNames) {
            builder.importedfield(ib -> ib.name(fieldName));
        }
    }

    private void docTypeBuildOneType(StructDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        var structBuilder = new DocumentmanagerConfig.Doctype.Structtype.Builder();
        structBuilder
            .idx(indexMap.idxOf(type))
            .name(type.getName());
        for (DataType inherited : type.getInheritedTypes()) {
            structBuilder.inherits(inheritBuilder -> inheritBuilder
                                   .type(indexMap.idxOf(inherited)));
            docTypeBuildAnyType(inherited, builder, indexMap);
        }
        for (com.yahoo.document.Field field : type.getFieldsThisTypeOnly()) {
            DataType fieldType = field.getDataType();
            structBuilder.field(fieldBuilder -> fieldBuilder
                                .name(field.getName())
                                .internalid(field.getId())
                                .type(indexMap.idxOf(fieldType)));
            docTypeBuildAnyType(fieldType, builder, indexMap);
        }
        builder.structtype(structBuilder);
    }

    private void docTypeBuildOneType(PrimitiveDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        builder.primitivetype(primBuilder -> primBuilder
                              .idx(indexMap.idxOf(type))
                              .name(type.getName()));
    }

    private void docTypeBuildOneType(TensorDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        var tt = type.getTensorType();
        String detailed = (tt != null) ? tt.toString() : "tensor";
        builder.tensortype(tensorBuilder -> tensorBuilder
                           .idx(indexMap.idxOf(type))
                           .detailedtype(detailed));

    }

    private void docTypeBuildOneType(ArrayDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        DataType nested = type.getNestedType();
        builder.arraytype(arrayBuilder -> arrayBuilder
                          .idx(indexMap.idxOf(type))
                          .elementtype(indexMap.idxOf(nested)));
        docTypeBuildAnyType(nested, builder, indexMap);
    }

    private void docTypeBuildOneType(WeightedSetDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        DataType nested = type.getNestedType();
        builder.wsettype(wsetBuilder -> wsetBuilder
                         .idx(indexMap.idxOf(type))
                         .elementtype(indexMap.idxOf(nested))
                         .createifnonexistent(type.createIfNonExistent())
                         .removeifzero(type.removeIfZero()));
        docTypeBuildAnyType(nested, builder, indexMap);
    }

    private void docTypeBuildOneType(MapDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        DataType keytype = type.getKeyType();
        DataType valtype = type.getValueType();
        builder.maptype(mapBuilder -> mapBuilder
                        .idx(indexMap.idxOf(type))
                        .keytype(indexMap.idxOf(keytype))
                        .valuetype(indexMap.idxOf(valtype)));
        docTypeBuildAnyType(keytype, builder, indexMap);
        docTypeBuildAnyType(valtype, builder, indexMap);
    }

    private void docTypeBuildOneType(AnnotationReferenceDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        builder.annotationref(arefBuilder -> arefBuilder
                              .idx(indexMap.idxOf(type))
                              .annotationtype(indexMap.idxOf(type.getAnnotationType())));
    }

    private void docTypeBuildOneType(NewDocumentReferenceDataType type,
                                     DocumentmanagerConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        builder.documentref(docrefBuilder -> docrefBuilder
                            .idx(indexMap.idxOf(type))
                            .targettype(indexMap.idxOf(type.getTargetType())));

    }

}
