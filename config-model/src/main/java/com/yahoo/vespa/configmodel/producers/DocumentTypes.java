// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configmodel.producers;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.OwnedTemporaryType;
import com.yahoo.documentmodel.TemporaryUnknownType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.vespa.documentmodel.DocumentModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author baldersheim
 */
public class DocumentTypes {

    private boolean useV8GeoPositions = false;

    public DocumentTypes useV8GeoPositions(boolean value) {
        this.useV8GeoPositions = value;
        return this;
    }

    public DocumenttypesConfig.Builder produce(DocumentModel model, DocumenttypesConfig.Builder builder) {
        builder.usev8geopositions(this.useV8GeoPositions);
        Map<NewDocumentType.Name, NewDocumentType> produced = new HashMap<>();
        var indexMap = new IdxMap();
        for (NewDocumentType documentType : model.getDocumentManager().getTypes()) {
            docTypeInheritOrder(documentType, builder, produced, indexMap);
        }
        indexMap.verifyAllDone();
        return builder;
    }

    static private <T> List<T> sortedList(Collection<T> unsorted, Comparator<T> cmp) {
        var list = new ArrayList<>(unsorted);
        list.sort(cmp);
        return list;
    }

    private void docTypeInheritOrder(NewDocumentType documentType,
                                     DocumenttypesConfig.Builder builder,
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
        private final Map<Integer, Boolean> doneMap = new HashMap<>();
        private final Map<Object, Integer> map = new IdentityHashMap<>();
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

    private void docTypeBuild(NewDocumentType documentType, DocumenttypesConfig.Builder builder, IdxMap indexMap) {
        DocumenttypesConfig.Doctype.Builder db = new DocumenttypesConfig.Doctype.Builder();
        db.
            idx(indexMap.idxOf(documentType)).
            name(documentType.getName()).
            internalid(documentType.getId()).
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

    private void docTypeBuildFieldSets(Set<FieldSet> fieldSets, DocumenttypesConfig.Doctype.Builder db) {
        for (FieldSet fs : fieldSets) {
            docTypeBuildOneFieldSet(fs, db);
        }
    }

    private void docTypeBuildOneFieldSet(FieldSet fs, DocumenttypesConfig.Doctype.Builder db) {
        db.fieldsets(fs.getName(), new DocumenttypesConfig.Doctype.Fieldsets.Builder().fields(fs.getFieldNames()));
    }

    private void docTypeBuildAnnotationType(AnnotationType annotation, DocumenttypesConfig.Doctype.Builder builder, IdxMap indexMap) {
        if (indexMap.isDone(annotation)) {
            return;
        }
        indexMap.setDone(annotation);
        var annBuilder = new DocumenttypesConfig.Doctype.Annotationtype.Builder();
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

    private void docTypeBuildAnyType(DataType type, DocumenttypesConfig.Doctype.Builder documentBuilder, IdxMap indexMap) {
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
        if (type instanceof TemporaryUnknownType) {
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

    private void docTypeBuildImportedFields(Collection<String> fieldNames, DocumenttypesConfig.Doctype.Builder builder) {
        for (String fieldName : fieldNames) {
            builder.importedfield(ib -> ib.name(fieldName));
        }
    }

    private void docTypeBuildOneType(StructDataType type,
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        var structBuilder = new DocumenttypesConfig.Doctype.Structtype.Builder();
        structBuilder
            .idx(indexMap.idxOf(type))
            .name(type.getName())
	    .internalid(type.getId());
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
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        builder.primitivetype(primBuilder -> primBuilder
                              .idx(indexMap.idxOf(type))
                              .name(type.getName()));
    }

    private void docTypeBuildOneType(TensorDataType type,
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        var tt = type.getTensorType();
        String detailed = (tt != null) ? tt.toString() : "tensor";
        builder.tensortype(tensorBuilder -> tensorBuilder
                           .idx(indexMap.idxOf(type))
                           .detailedtype(detailed));

    }

    private void docTypeBuildOneType(ArrayDataType type,
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        DataType nested = type.getNestedType();
        builder.arraytype(arrayBuilder -> arrayBuilder
                          .idx(indexMap.idxOf(type))
                          .elementtype(indexMap.idxOf(nested))
			  .internalid(type.getId()));
        docTypeBuildAnyType(nested, builder, indexMap);
    }

    private void docTypeBuildOneType(WeightedSetDataType type,
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        DataType nested = type.getNestedType();
        builder.wsettype(wsetBuilder -> wsetBuilder
                         .idx(indexMap.idxOf(type))
                         .elementtype(indexMap.idxOf(nested))
                         .createifnonexistent(type.createIfNonExistent())
                         .removeifzero(type.removeIfZero())
			 .internalid(type.getId()));
        docTypeBuildAnyType(nested, builder, indexMap);
    }

    private void docTypeBuildOneType(MapDataType type,
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        DataType keytype = type.getKeyType();
        DataType valtype = type.getValueType();
        builder.maptype(mapBuilder -> mapBuilder
                        .idx(indexMap.idxOf(type))
                        .keytype(indexMap.idxOf(keytype))
                        .valuetype(indexMap.idxOf(valtype))
			.internalid(type.getId()));
        docTypeBuildAnyType(keytype, builder, indexMap);
        docTypeBuildAnyType(valtype, builder, indexMap);
    }

    private void docTypeBuildOneType(AnnotationReferenceDataType type,
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        builder.annotationref(arefBuilder -> arefBuilder
                              .idx(indexMap.idxOf(type))
                              .annotationtype(indexMap.idxOf(type.getAnnotationType()))
			      .internalid(type.getId()));
    }

    private void docTypeBuildOneType(NewDocumentReferenceDataType type,
                                     DocumenttypesConfig.Doctype.Builder builder,
                                     IdxMap indexMap)
    {
        builder.documentref(docrefBuilder -> docrefBuilder
                            .idx(indexMap.idxOf(type))
                            .targettype(indexMap.idxOf(type.getTargetType()))
			    .internalid(type.getId()));

    }

}
