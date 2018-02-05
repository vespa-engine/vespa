// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.documentmodel.DataTypeCollection;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.annotation.SDAnnotationType;
import com.yahoo.searchdefinition.document.annotation.TemporaryAnnotationReferenceDataType;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.documentmodel.FieldView;
import com.yahoo.vespa.documentmodel.SearchDef;
import com.yahoo.vespa.documentmodel.SearchField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

/**
 * @author baldersheim
 */
public class DocumentModelBuilder {

    private DocumentModel model;
    private final Map<NewDocumentType, List<SDDocumentType>> scratchInheritsMap = new HashMap<>();

    public DocumentModelBuilder(DocumentModel model) {
        this.model = model;
        model.getDocumentManager().add(VespaDocumentType.INSTANCE);
    }

    public boolean valid() {
        return scratchInheritsMap.isEmpty();
    }

    public void addToModel(Collection<Search> searchList) {
        List<SDDocumentType> docList = new LinkedList<>();
        for (Search search : searchList) {
            docList.add(search.getDocument());
        }
        docList = sortDocumentTypes(docList);
        addDocumentTypes(docList);
        for (Collection<Search> toAdd = tryAdd(searchList);
             ! toAdd.isEmpty() && (toAdd.size() < searchList.size());
             toAdd = tryAdd(searchList)) {
            searchList = toAdd;
        }
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

    private Collection<Search> tryAdd(Collection<Search> searchList) {
        Collection<Search> left = new ArrayList<>();
        for (Search search : searchList) {
            try {
                addToModel(search);
            } catch (RetryLaterException e) {
                left.add(search);
            }
        }
        return left;
    }

    public void addToModel(Search search) {
        // Then we add the search specific stuff
        SearchDef searchDef = new SearchDef(search.getName());
        addSearchFields(search.extraFieldList(), searchDef);
        for (Field f : search.getDocument().fieldSet()) {
            addSearchField((SDField) f, searchDef);
        }
        for (SDField field : search.allConcreteFields()) {
            for (Attribute attribute : field.getAttributes().values()) {
                if ( ! searchDef.getFields().containsKey(attribute.getName())) {
                    searchDef.add(new SearchField(new Field(attribute.getName(), field), !field.getIndices().isEmpty(), true));
                }
            }
        }

        for (Field f : search.getDocument().fieldSet()) {
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
        for(NewDocumentType doc : lst) {
            resolveTemporaries(doc.getAllTypes(), lst);
        }
    }
    private static void resolveTemporaries(DataTypeCollection dtc, Collection<NewDocumentType> docs) {
        for (DataType type : dtc.getTypes()) {
            resolveTemporariesRecurse(type, dtc, docs);
        }
    }

    @SuppressWarnings("deprecation")
    private static DataType resolveTemporariesRecurse(DataType type, DataTypeCollection repo,
                                                      Collection<NewDocumentType> docs) {
        if (type instanceof TemporaryStructuredDataType) {
            NewDocumentType docType = getDocumentType(docs, type.getId());
            if (docType != null) {
                type = docType;
                return type;
            }
            DataType real = repo.getDataType(type.getId());
            if (real == null) {
                throw new NullPointerException("Can not find type '" + type.toString() + "', impossible.");
            }
            type = real;
        } else if (type instanceof StructDataType) {
            StructDataType dt = (StructDataType) type;
            for (com.yahoo.document.Field field : dt.getFields()) {
                if (field.getDataType() != type) {
                    // XXX deprecated:
                    field.setDataType(resolveTemporariesRecurse(field.getDataType(), repo, docs));
                }
            }
        } else if (type instanceof MapDataType) {
            MapDataType t = (MapDataType) type;
            t.setKeyType(resolveTemporariesRecurse(t.getKeyType(), repo, docs));
            t.setValueType(resolveTemporariesRecurse(t.getValueType(), repo, docs));
        } else if (type instanceof CollectionDataType) {
            CollectionDataType t = (CollectionDataType) type;
            t.setNestedType(resolveTemporariesRecurse(t.getNestedType(), repo, docs));
        } else if (type instanceof ReferenceDataType) {
            ReferenceDataType t = (ReferenceDataType) type;
            if (t.getTargetType() instanceof TemporaryStructuredDataType) {
                DataType targetType = resolveTemporariesRecurse(t.getTargetType(), repo, docs);
                t.setTargetType((StructuredDataType) targetType);
            }
        }
        return type;
    }

    private static NewDocumentType getDocumentType(Collection<NewDocumentType> docs, int id) {
        for (NewDocumentType doc : docs) {
            if (doc.getId() == id) {
                return doc;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static void specialHandleAnnotationReference(NewDocumentType docType, Field field) {
        DataType fieldType = specialHandleAnnotationReferenceRecurse(docType, field.getName(), field.getDataType());
        if (fieldType == null) {
            return;
        }
        field.setDataType(fieldType); // XXX deprecated
    }

    private static DataType specialHandleAnnotationReferenceRecurse(NewDocumentType docType, String fieldName,
                                                                    DataType dataType)
    {
        if (dataType instanceof TemporaryAnnotationReferenceDataType) {
            TemporaryAnnotationReferenceDataType refType = (TemporaryAnnotationReferenceDataType)dataType;
            if (refType.getId() != 0) {
                return null;
            }
            AnnotationType target = docType.getAnnotationType(refType.getTarget());
            if (target == null) {
                throw new RetryLaterException("Annotation '" + refType.getTarget() + "' in reference '" + fieldName +
                                              "' does not exist.");
            }
            dataType = new AnnotationReferenceDataType(target);
            addType(docType, dataType);
            return dataType;
        } else if (dataType instanceof MapDataType) {
            MapDataType mapType = (MapDataType)dataType;
            DataType valueType = specialHandleAnnotationReferenceRecurse(docType, fieldName, mapType.getValueType());
            if (valueType == null) {
                return null;
            }
            mapType = mapType.clone();
            mapType.setValueType(valueType);
            addType(docType, mapType);
            return mapType;
        } else if (dataType instanceof CollectionDataType) {
            CollectionDataType lstType = (CollectionDataType)dataType;
            DataType nestedType = specialHandleAnnotationReferenceRecurse(docType, fieldName, lstType.getNestedType());
            if (nestedType == null) {
                return null;
            }
            lstType = lstType.clone();
            lstType.setNestedType(nestedType);
            addType(docType, lstType);
            return lstType;
        }
        return null;
    }

    private static StructDataType handleStruct(NewDocumentType dt, SDDocumentType type) {
        StructDataType s = new StructDataType(type.getName());
        for (Field f : type.getDocumentType().getHeaderType().getFieldsThisTypeOnly()) {
            specialHandleAnnotationReference(dt, f);
            s.addField(f);
        }
        for (StructDataType inherited : type.getDocumentType().getHeaderType().getInheritedTypes()) {
            s.inherit(inherited);
        }
        extractNestedTypes(dt, s);
        addType(dt, s);
        return s;
    }

    private static StructDataType handleStruct(NewDocumentType dt, StructDataType s) {
        for (Field f : s.getFieldsThisTypeOnly()) {
            specialHandleAnnotationReference(dt, f);
        }
        extractNestedTypes(dt, s);
        addType(dt, s);
        return s;
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
        Map<AnnotationType, String> annotationInheritance = new HashMap<>();
        Map<StructDataType, String> structInheritance = new HashMap<>();
        NewDocumentType dt = new NewDocumentType(new NewDocumentType.Name(sdoc.getName()),
                                                 sdoc.getDocumentType().getHeaderType(),
                                                 sdoc.getDocumentType().getBodyType(),
                                                 sdoc.getFieldSets(),
                                                 convertDocumentReferencesToNames(sdoc.getDocumentReferences()));
        for (SDDocumentType n : sdoc.getInheritedTypes()) {
            NewDocumentType.Name name = new NewDocumentType.Name(n.getName());
                NewDocumentType inherited =  model.getDocumentManager().getDocumentType(name);
                if (inherited != null) {
                    dt.inherit(inherited);
                }
        }
        for (SDDocumentType type : sdoc.getTypes()) {
            if (type.isStruct()) {
                handleStruct(dt, type);
            } else {
                throw new IllegalArgumentException("Data type '" + sdoc.getName() + "' is not a struct => tostring='" + sdoc.toString() + "'.");
            }
        }
        for (AnnotationType annotation : sdoc.getAnnotations()) {
            dt.add(annotation);
        }
        for (AnnotationType annotation : sdoc.getAnnotations()) {
            SDAnnotationType sa = (SDAnnotationType) annotation;
            if (annotation.getInheritedTypes().isEmpty() && (sa.getInherits() != null) ) {
                annotationInheritance.put(annotation, sa.getInherits());
            }
            if (annotation.getDataType() == null) {
                if (sa.getSdDocType() != null) {
                    StructDataType s = handleStruct(dt, sa.getSdDocType());
                    annotation.setDataType(s);
                    if ((sa.getInherits() != null)) {
                        structInheritance.put(s, "annotation."+sa.getInherits());
                    }
                } else if (sa.getInherits() != null) {
                    StructDataType s = new StructDataType("annotation."+annotation.getName());
                    if (anyParentsHavePayLoad(sa, sdoc)) {
                        annotation.setDataType(s);
                        addType(dt, s);
                    }
                    structInheritance.put(s, "annotation."+sa.getInherits());
                }
            }
        }
        for (Map.Entry<AnnotationType, String> e : annotationInheritance.entrySet()) {
            e.getKey().inherit(dt.getAnnotationType(e.getValue()));
        }
        for (Map.Entry<StructDataType, String> e : structInheritance.entrySet()) {
            StructDataType s = (StructDataType)dt.getDataType(e.getValue());
            if (s != null) {
                e.getKey().inherit(s);
            }
        }
        handleStruct(dt, sdoc.getDocumentType().getHeaderType());
        handleStruct(dt, sdoc.getDocumentType().getBodyType());

        extractDataTypesFromFields(dt, sdoc.fieldSet());
        return dt;
    }

    private static Set<NewDocumentType.Name> convertDocumentReferencesToNames(Optional<DocumentReferences> documentReferences) {
        if (!documentReferences.isPresent()) {
            return emptySet();
        }
        return documentReferences.get().referenceMap().values().stream()
                .map(documentReference -> documentReference.targetSearch().getDocument())
                .map(documentType -> new NewDocumentType.Name(documentType.getName()))
                .collect(toSet());
    }

    private static void extractDataTypesFromFields(NewDocumentType dt, Collection<Field> fields) {
        for (Field f : fields) {
            DataType type = f.getDataType();
            if (testAddType(dt, type)) {
                extractNestedTypes(dt, type);
                addType(dt, type);
            }
        }
    }
    private static void extractNestedTypes(NewDocumentType dt, DataType type) {
        if (type instanceof StructDataType) {
            StructDataType tmp = (StructDataType) type;
            extractDataTypesFromFields(dt, tmp.getFieldsThisTypeOnly());
        } else if (type instanceof DocumentType) {
            throw new IllegalArgumentException("Can not handle nested document definitions. In document type '" + dt.getName().toString() +
                                               "', we can not define document type '" + type.toString());
        } else if (type instanceof CollectionDataType) {
            CollectionDataType tmp = (CollectionDataType) type;
            extractNestedTypes(dt, tmp.getNestedType());
            addType(dt, tmp.getNestedType());
        } else if (type instanceof MapDataType) {
            MapDataType tmp = (MapDataType) type;
            extractNestedTypes(dt, tmp.getKeyType());
            extractNestedTypes(dt, tmp.getValueType());
            addType(dt, tmp.getKeyType());
            addType(dt, tmp.getValueType());
        } else if (type instanceof TemporaryAnnotationReferenceDataType) {
            throw new IllegalArgumentException(type.toString());
        }
    }
    private static boolean testAddType(NewDocumentType dt, DataType type) { return internalAddType(dt, type, true); }
    private static boolean addType(NewDocumentType dt, DataType type) { return internalAddType(dt, type, false); }
    private static boolean internalAddType(NewDocumentType dt, DataType type, boolean dryRun) {
        DataType oldType = dt.getDataTypeRecursive(type.getId());
        if (oldType == null) {
            if ( ! dryRun) {
                dt.add(type);
            }
            return true;
        } else if ((type instanceof StructDataType) && (oldType instanceof StructDataType)) {
            StructDataType s = (StructDataType) type;
            StructDataType os = (StructDataType) oldType;
            if ((os.getFieldCount() == 0) && (s.getFieldCount() > os.getFieldCount())) {
                if ( ! dryRun) {
                    dt.replace(type);
                }
                return true;
            }
        }
        return false;
    }

    public static class RetryLaterException extends IllegalArgumentException {
        public RetryLaterException(String message) {
            super(message);
        }
    }

}
