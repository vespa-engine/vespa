// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.DataTypeCollection;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.OwnedStructDataType;
import com.yahoo.documentmodel.OwnedTemporaryType;
import com.yahoo.documentmodel.TemporaryUnknownType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporaryImportedFields;
import com.yahoo.schema.document.annotation.SDAnnotationType;
import com.yahoo.schema.document.annotation.TemporaryAnnotationReferenceDataType;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.documentmodel.FieldView;
import com.yahoo.vespa.documentmodel.SearchDef;
import com.yahoo.vespa.documentmodel.SearchField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author baldersheim
 */
public class DocumentModelBuilder {

    private final DocumentModel model;

    public DocumentModelBuilder() {
        this.model = new DocumentModel();
        this.model.getDocumentManager().add(VespaDocumentType.INSTANCE);
    }

    public DocumentModel build(Collection<Schema> schemaList) {
        List<SDDocumentType> docList = new LinkedList<>();
        for (Schema schema : schemaList) {
            docList.add(schema.getDocument());
        }
        docList = sortDocumentTypes(docList);
        addDocumentTypes(docList);
        for (Collection<Schema> toAdd = tryAdd(schemaList);
             ! toAdd.isEmpty() && (toAdd.size() < schemaList.size());
             toAdd = tryAdd(schemaList)) {
            schemaList = toAdd;
        }
        return model;
    }

    private List<SDDocumentType> sortDocumentTypes(List<SDDocumentType> docList) {
        Set<String> doneNames = new HashSet<>();
        doneNames.add(SDDocumentType.VESPA_DOCUMENT.getName());
        List<SDDocumentType> doneList = new LinkedList<>();
        List<SDDocumentType> prevList = null;
        List<SDDocumentType> nextList = docList;
        while (prevList == null || nextList.size() < prevList.size()) {
            prevList = nextList;
            nextList = new LinkedList<>();
            for (SDDocumentType doc : prevList) {
                boolean isDone = true;
                for (SDDocumentType inherited : doc.getInheritedTypes()) {
                    if (!doneNames.contains(inherited.getName())) {
                        isDone = false;
                        break;
                    }
                }
                if (isDone) {
                    doneNames.add(doc.getName());
                    doneList.add(doc);
                } else {
                    nextList.add(doc);
                }
            }
        }
        if (!nextList.isEmpty()) {
            throw new IllegalArgumentException("Could not resolve inheritance of document types " +
                                               toString(prevList) + ".");
        }
        return doneList;
    }

    private static String toString(List<SDDocumentType> lst) {
        StringBuilder out = new StringBuilder();
        for (int i = 0, len = lst.size(); i < len; ++i) {
            out.append("'").append(lst.get(i).getName()).append("'");
            if (i < len - 2) {
                out.append(", ");
            } else if (i < len - 1) {
                out.append(" and ");
            }
        }
        return out.toString();
    }

    private Collection<Schema> tryAdd(Collection<Schema> schemaList) {
        Collection<Schema> left = new ArrayList<>();
        for (Schema schema : schemaList) {
            try {
                addToModel(schema);
            } catch (RetryLaterException e) {
                left.add(schema);
            }
        }
        return left;
    }

    private void addToModel(Schema schema) {
        // Then we add the search specific stuff
        SearchDef searchDef = new SearchDef(schema.getName());
        addSearchFields(schema.extraFieldList(), searchDef);
        for (Field f : schema.getDocument().fieldSet()) {
            addSearchField((SDField) f, searchDef);
        }
        for (SDField field : schema.allConcreteFields()) {
            for (Attribute attribute : field.getAttributes().values()) {
                if ( ! searchDef.getFields().containsKey(attribute.getName())) {
                    searchDef.add(new SearchField(new Field(attribute.getName(), field), !field.getIndices().isEmpty(), true));
                }
            }
        }

        for (Field f : schema.getDocument().fieldSet()) {
            addAlias((SDField) f, searchDef);
        }
        model.getSearchManager().add(searchDef);
    }

    private static void addSearchFields(Collection<SDField> fields, SearchDef searchDef) {
        for (SDField field : fields) {
            addSearchField(field, searchDef);
        }
    }

    private static void addSearchField(SDField field, SearchDef searchDef) {
        SearchField searchField =
            new SearchField(field,
                            field.getIndices().containsKey(field.getName()) && field.getIndices().get(field.getName()).getType().equals(Index.Type.VESPA),
                            field.getAttributes().containsKey(field.getName()));
        searchDef.add(searchField);

        // Add field to views
        addToView(field.getIndices().keySet(), searchField, searchDef);
    }

    private static void addAlias(SDField field, SearchDef searchDef) {
        for (Map.Entry<String, String> entry : field.getAliasToName().entrySet()) {
            searchDef.addAlias(entry.getKey(), entry.getValue());
        }
    }

    private static void addToView(Collection<String> views, Field field, SearchDef searchDef) {
        for (String viewName : views) {
            addToView(viewName, field, searchDef);
        }
    }

    private static void addToView(String viewName, Field field, SearchDef searchDef) {
        if (searchDef.getViews().containsKey(viewName)) {
            searchDef.getViews().get(viewName).add(field);
        } else {
            if (!searchDef.getFields().containsKey(viewName)) {
                FieldView view = new FieldView(viewName);
                view.add(field);
                searchDef.add(view);
            }
        }
    }

    private void addDocumentTypes(List<SDDocumentType> docList) {
        LinkedList<NewDocumentType> lst = new LinkedList<>();
        for (SDDocumentType doc : docList) {
            lst.add(convert(doc));
            model.getDocumentManager().add(lst.getLast());
        }
        Map<DataType, DataType> replacements = new IdentityHashMap<>();
        for(NewDocumentType doc : lst) {
            resolveTemporaries(doc.getAllTypes(), lst, replacements);
            resolveTemporariesRecurse(doc.getContentStruct(), doc.getAllTypes(), lst, replacements);
        }
        for(NewDocumentType doc : lst) {
            for (var entry : replacements.entrySet()) {
                var old = entry.getKey();
                if (doc.getDataType(old.getId()) == old) {
                    doc.replace(entry.getValue());
                }
            }
        }
    }

    private static void resolveTemporaries(DataTypeCollection dtc,
                                           Collection<NewDocumentType> docs,
                                           Map<DataType, DataType> replacements) {
        for (DataType type : dtc.getTypes()) {
            resolveTemporariesRecurse(type, dtc, docs, replacements);
        }
    }

    @SuppressWarnings("deprecation")
    private static DataType resolveTemporariesRecurse(DataType type, DataTypeCollection repo,
                                                      Collection<NewDocumentType> docs,
                                                      Map<DataType, DataType> replacements) {
        if (replacements.containsKey(type)) {
            return replacements.get(type);
        }
        DataType original = type;
        if (type instanceof TemporaryUnknownType) {
            // must be a known struct or document type
            DataType other = repo.getDataType(type.getId());
            if (other == null || other == type) {
                // maybe it is the name of a document type:
                other = getDocumentType(docs, type.getName());
            }
            if (other == null) {
                throw new IllegalArgumentException("No replacement found for temporary type: " + type);
            }
            type = other;
        } else if (type instanceof OwnedTemporaryType) {
            // must be replaced with the real struct type
            DataType other = repo.getDataType(type.getId());
            if (other == null || other == type) {
                throw new IllegalArgumentException("No replacement found for temporary type: " + type);
            }
            if (other instanceof OwnedStructDataType otherOwned) {
                var owned = (OwnedTemporaryType) type;
                String ownedBy = owned.getOwnerName();
                String otherOwnedBy = otherOwned.getOwnerName();
                if (! ownedBy.equals(otherOwnedBy)) {
                    throw new IllegalArgumentException("Wrong document for type: " + otherOwnedBy + " but expected " + ownedBy);
                }
            } else {
                throw new IllegalArgumentException("Found wrong sort of type: " + other + " [" + other.getClass() + "]");
            }
            type = other;
        } else if (type instanceof DocumentType) {
            DataType other = getDocumentType(docs, type.getName());
            if (other != null) {
                type = other;
            } else if (type != DataType.DOCUMENT) {
                throw new IllegalArgumentException
                    ("Can not handle nested document definitions. Undefined document type: " + type);
            }
        } else if (type instanceof NewDocumentType) {
            DataType other = getDocumentType(docs, type.getName());
            if (other != null) {
                type = other;
            }
        } else if (type instanceof StructDataType sdt) {
            // trick avoids infinite recursion:
            var old = replacements.put(original, type);
            assert(old == null);
            for (com.yahoo.document.Field field : sdt.getFields()) {
                var ft = field.getDataType();
                var newft = resolveTemporariesRecurse(ft, repo, docs, replacements);
                if (ft != newft) {
                    // XXX deprecated:
                    field.setDataType(newft);
                }
            }
            old = replacements.remove(original);
            assert(old == type);
        }
        else if (type instanceof MapDataType mdt) {
            var old_kt = mdt.getKeyType();
            var old_vt = mdt.getValueType();
            var kt = resolveTemporariesRecurse(old_kt, repo, docs, replacements);
            var vt = resolveTemporariesRecurse(old_vt, repo, docs, replacements);
            if (kt != old_kt || vt != old_vt) {
                type = new MapDataType(kt, vt, mdt.getId());
            }
        }
        else if (type instanceof ArrayDataType adt) {
            var old_nt = adt.getNestedType();
            var nt = resolveTemporariesRecurse(old_nt, repo, docs, replacements);
            if (nt != old_nt) {
                type = new ArrayDataType(nt, adt.getId());
            }
        }
        else if (type instanceof WeightedSetDataType wdt) {
            var old_nt = wdt.getNestedType();
            var nt = resolveTemporariesRecurse(old_nt, repo, docs, replacements);
            if (nt != old_nt) {
                boolean c = wdt.createIfNonExistent();
                boolean r = wdt.removeIfZero();
                type = new WeightedSetDataType(nt, c, r, wdt.getId());
            }
        }
        else if (type instanceof NewDocumentReferenceDataType rft) {
            var doc = getDocumentType(docs, rft.getTargetTypeName());
            type = doc.getReferenceDataType();
        }
        if (type != original) {
            replacements.put(original, type);
        }
        return type;
    }

    private static NewDocumentType getDocumentType(Collection<NewDocumentType> docs, String name) {
        for (NewDocumentType doc : docs) {
            if (doc.getName().equals(name)) {
                return doc;
            }
        }
        return null;
    }

    private static boolean anyParentsHavePayLoad(SDAnnotationType sa, SDDocumentType sdoc) {
        if (sa.getInherits() != null) {
            AnnotationType tmp = sdoc.findAnnotation(sa.getInherits());
            SDAnnotationType inherited = (SDAnnotationType) tmp;
            return ((inherited.getSdDocType() != null) || anyParentsHavePayLoad(inherited, sdoc));
        }
        return false;
    }

    private NewDocumentType convert(SDDocumentType sdoc) {
        NewDocumentType dt = new NewDocumentType(new NewDocumentType.Name(sdoc.getName()),
                                                 sdoc.getDocumentType().contentStruct(),
                                                 sdoc.getFieldSets(),
                                                 convertDocumentReferencesToNames(sdoc.getDocumentReferences()),
                                                 convertTemporaryImportedFieldsToNames(sdoc.getTemporaryImportedFields()));
        for (SDDocumentType n : sdoc.getInheritedTypes()) {
            NewDocumentType.Name name = new NewDocumentType.Name(n.getName());
            NewDocumentType inherited = model.getDocumentManager().getDocumentType(name);
            if (inherited != null) {
                dt.inherit(inherited);
            }
        }
        var extractor = new TypeExtractor(dt);
        extractor.extract(sdoc);
        return dt;
    }

    static class TypeExtractor {
        private final NewDocumentType targetDt;
        Map<AnnotationType, String> annotationInheritance = new LinkedHashMap<>();
        Map<StructDataType, String> structInheritance = new LinkedHashMap<>();
        private final Map<Object, Object> inProgress = new IdentityHashMap<>();
        TypeExtractor(NewDocumentType target) {
            this.targetDt = target;
        }

        void extract(SDDocumentType sdoc) {
            for (SDDocumentType type : sdoc.getTypes()) {
                if (type.isStruct()) {
                    handleStruct(type);
                } else {
                    throw new IllegalArgumentException("Data type '" + type.getName() + "' is not a struct => tostring='" + type + "'.");
                }
            }
            for (SDDocumentType type : sdoc.getTypes()) {
                for (SDDocumentType proxy : type.getInheritedTypes()) {
                    var inherited = (StructDataType) targetDt.getDataTypeRecursive(proxy.getName());
                    var converted = (StructDataType) targetDt.getDataType(type.getName());
                    assert(converted instanceof OwnedStructDataType);
                    assert(inherited instanceof OwnedStructDataType);
                    if (! converted.inherits(inherited)) {
                        converted.inherit(inherited);
                    }
                }
            }
            for (AnnotationType annotation : sdoc.getAnnotations().values()) {
                targetDt.add(annotation);
            }
            for (AnnotationType annotation : sdoc.getAnnotations().values()) {
                SDAnnotationType sa = (SDAnnotationType) annotation;
                if (annotation.getInheritedTypes().isEmpty() && (sa.getInherits() != null) ) {
                    annotationInheritance.put(annotation, sa.getInherits());
                }
                if (annotation.getDataType() == null) {
                    if (sa.getSdDocType() != null) {
                        StructDataType s = handleStruct(sa.getSdDocType());
                        annotation.setDataType(s);
                        if ((sa.getInherits() != null)) {
                            structInheritance.put(s, "annotation." + sa.getInherits());
                        }
                    } else if (sa.getInherits() != null) {
                        StructDataType s = new OwnedStructDataType("annotation." + annotation.getName(), sdoc.getName());
                        if (anyParentsHavePayLoad(sa, sdoc)) {
                            annotation.setDataType(s);
                            addType(s);
                        }
                        structInheritance.put(s, "annotation." + sa.getInherits());
                    }
                } else {
                    var dt = annotation.getDataType();
                    if (dt instanceof StructDataType) {
                        handleStruct((StructDataType) dt);
                    }
                }
            }
            for (Map.Entry<AnnotationType, String> e : annotationInheritance.entrySet()) {
                e.getKey().inherit(targetDt.getAnnotationType(e.getValue()));
            }
            for (Map.Entry<StructDataType, String> e : structInheritance.entrySet()) {
                StructDataType s = (StructDataType)targetDt.getDataType(e.getValue());
                if (s != null) {
                    e.getKey().inherit(s);
                }
            }
            handleStruct(sdoc.getDocumentType().contentStruct());
            extractDataTypesFromFields(sdoc.fieldSet());
        }

        private void extractDataTypesFromFields(Collection<Field> fields) {
            for (Field f : fields) {
                DataType type = f.getDataType();
                if (testAddType(type)) {
                    extractNestedTypes(type);
                    addType(type);
                }
            }
        }

        private void extractNestedTypes(DataType type) {
            if (inProgress.containsKey(type)) {
                return;
            }
            inProgress.put(type, this);
            if (type instanceof StructDataType sdt) {
                extractDataTypesFromFields(sdt.getFieldsThisTypeOnly());
            } else if (type instanceof CollectionDataType cdt) {
                extractNestedTypes(cdt.getNestedType());
                addType(cdt.getNestedType());
            } else if (type instanceof MapDataType mdt) {
                extractNestedTypes(mdt.getKeyType());
                extractNestedTypes(mdt.getValueType());
                addType(mdt.getKeyType());
                addType(mdt.getValueType());
            } else if (type instanceof TemporaryAnnotationReferenceDataType) {
                throw new IllegalArgumentException(type.toString());
            }
        }

        private boolean testAddType(DataType type) { return internalAddType(type, true); }

        private void addType(DataType type) { internalAddType(type, false); }

        private boolean internalAddType(DataType type, boolean dryRun) {
            DataType oldType = targetDt.getDataTypeRecursive(type.getId());
            if (oldType == null) {
                if ( ! dryRun) {
                    targetDt.add(type);
                }
                return true;
            }
            if (oldType == type) {
                return false;
            }
            if (targetDt.getDataType(type.getId()) == null) {
                if ((oldType instanceof OwnedStructDataType oldOwned)
                    && (type instanceof OwnedStructDataType newOwned))
                {
                    if (newOwned.getOwnerName().equals(targetDt.getName()) &&
                        ! oldOwned.getOwnerName().equals(targetDt.getName()))
                    {
                        if ( ! dryRun) {
                            targetDt.add(type);
                        }
                        return true;
                    }
                }
            }
            if ((type instanceof StructDataType sdt) && (oldType instanceof StructDataType oldSdt)) {
                if ((oldSdt.getFieldCount() == 0) && (sdt.getFieldCount() > oldSdt.getFieldCount())) {
                    if ( ! dryRun) {
                        targetDt.replace(type);
                    }
                    return true;
                }
            }
            return false;
        }


        @SuppressWarnings("deprecation")
        private void specialHandleAnnotationReference(Field field) {
            DataType fieldType = specialHandleAnnotationReferenceRecurse(field.getName(), field.getDataType());
            if (fieldType == null) {
                return;
            }
            field.setDataType(fieldType); // XXX deprecated
        }

        private DataType specialHandleAnnotationReferenceRecurse(String fieldName,
                                                                 DataType dataType) {
            if (dataType instanceof TemporaryAnnotationReferenceDataType refType) {
                if (refType.getId() != 0) {
                    return null;
                }
                AnnotationType target = targetDt.getAnnotationType(refType.getTarget());
                if (target == null) {
                    throw new RetryLaterException("Annotation '" + refType.getTarget() + "' in reference '" + fieldName +
                                                  "' does not exist.");
                }
                dataType = new AnnotationReferenceDataType(target);
                addType(dataType);
                return dataType;
            }
            else if (dataType instanceof MapDataType mdt) {
                DataType valueType = specialHandleAnnotationReferenceRecurse(fieldName, mdt.getValueType());
                if (valueType == null) {
                    return null;
                }
                var mapType = new MapDataType(mdt.getKeyType(), valueType, mdt.getId());
                addType(mapType);
                return mapType;
            }
            else if (dataType instanceof ArrayDataType adt) {
                DataType nestedType = specialHandleAnnotationReferenceRecurse(fieldName, adt.getNestedType());
                if (nestedType == null) {
                    return null;
                }
                var lstType = new ArrayDataType(nestedType, adt.getId());
                addType(lstType);
                return lstType;
            }
            else if (dataType instanceof WeightedSetDataType wdt) {
                DataType nestedType = specialHandleAnnotationReferenceRecurse(fieldName, wdt.getNestedType());
                if (nestedType == null) {
                    return null;
                }
                boolean c = wdt.createIfNonExistent();
                boolean r = wdt.removeIfZero();
                var lstType = new WeightedSetDataType(nestedType, c, r, wdt.getId());
                addType(lstType);
                return lstType;
            }
            return null;
        }

        private StructDataType handleStruct(SDDocumentType type) {
            if (type.isStruct()) {
                var st = type.getStruct();
                if (st.getName().equals(type.getName()) &&
                    (st instanceof StructDataType) &&
                    (! (st instanceof TemporaryUnknownType)) &&
                    (! (st instanceof OwnedTemporaryType)))
                    {
                        return handleStruct((StructDataType) st);
                    }
            }
            StructDataType s = new OwnedStructDataType(type.getName(), targetDt.getName());
            for (Field f : type.getDocumentType().contentStruct().getFieldsThisTypeOnly()) {
                specialHandleAnnotationReference(f);
                s.addField(f);
            }
            for (StructDataType inherited : type.getDocumentType().contentStruct().getInheritedTypes()) {
                s.inherit(inherited);
            }
            extractNestedTypes(s);
            addType(s);
            return s;
        }

        private StructDataType handleStruct(StructDataType s) {
            for (Field f : s.getFieldsThisTypeOnly()) {
                specialHandleAnnotationReference(f);
            }
            extractNestedTypes(s);
            addType(s);
            return s;
        }

    }

    private static Set<NewDocumentType.Name> convertDocumentReferencesToNames(Optional<DocumentReferences> documentReferences) {
        if (documentReferences.isEmpty()) {
            return Set.of();
        }
        return documentReferences.get().referenceMap().values().stream()
            .map(documentReference -> documentReference.targetSearch().getDocument())
            .map(documentType -> new NewDocumentType.Name(documentType.getName()))
            .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

    private static Set<String> convertTemporaryImportedFieldsToNames(TemporaryImportedFields importedFields) {
        if (importedFields == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(importedFields.fields().keySet());
    }

    public static class RetryLaterException extends IllegalArgumentException {
        public RetryLaterException(String message) {
            super(message);
        }
    }

}
