// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.*;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.TemporarySDDocumentType;

import java.util.*;
import java.util.logging.Level;

/**
 * @author Einar M R Rosenvinge
 */
public class SDDocumentTypeOrderer {

    private Map<DataTypeName, SDDocumentType> createdSDTypes = new LinkedHashMap<>();
    private Set<Integer> seenTypes = new LinkedHashSet<>();
    List<SDDocumentType> processingOrder = new LinkedList<>();
    private DeployLogger deployLogger;

    public SDDocumentTypeOrderer(List<SDDocumentType> sdTypes, DeployLogger deployLogger) {
        this.deployLogger = deployLogger;
        for (SDDocumentType type : sdTypes) {
            createdSDTypes.put(type.getDocumentName(), type);
        }
        DocumentTypeManager dtm = new DocumentTypeManager();
        for (DataType type : dtm.getDataTypes()) {
            seenTypes.add(type.getId());
        }

    }

    List<SDDocumentType> getOrdered() { return processingOrder; }

    public void process() {
        for (SDDocumentType type : createdSDTypes.values()) {
            process(type);
        }
    }
    private void process(SDDocumentType type) {
        List<DataTypeName> toReplace = new ArrayList<>();
        for (SDDocumentType sdoc : type.getInheritedTypes()) {
            if (sdoc instanceof TemporarySDDocumentType) {
                toReplace.add(sdoc.getDocumentName());
            }
        }
        for (DataTypeName name : toReplace) {
            SDDocumentType inherited = createdSDTypes.get(name);
            if (inherited == null) {
                throw new IllegalStateException("Document type '" + name + "' not found.");
            }
            process(inherited);
            type.inherit(inherited);
        }
        visit(type);
    }

    private void visit(SDDocumentType docOrStruct) {
        int id;
        if (docOrStruct.isStruct()) {
            id = new StructDataType(docOrStruct.getName()).getId();
        } else {
            id = new DocumentType(docOrStruct.getName()).getId();
        }

        if (seenTypes.contains(id)) {
            return;
        } else {
            seenTypes.add((new StructDataType(docOrStruct.getName()).getId()));
        }


        for (Field field : docOrStruct.fieldSet()) {
            if (!seenTypes.contains(field.getDataType().getId())) {
                //we haven't seen this before, do it
                visit(field.getDataType());
            }
        }
        processingOrder.add(docOrStruct);
    }

    private SDDocumentType find(String name) {
        SDDocumentType sdDocType = createdSDTypes.get(new DataTypeName(name));
        if (sdDocType != null) {
            return sdDocType;
        }
        for(SDDocumentType sdoc : createdSDTypes.values()) {
             for (SDDocumentType stype : sdoc.getTypes()) {
                 if (stype.getName().equals(name)) {
                    return stype;
                 }
             }
        }
        return null;
    }
    private void visit(DataType type) {
        if (type instanceof StructuredDataType) {
            StructuredDataType structType = (StructuredDataType) type;
            SDDocumentType sdDocType = find(structType.getName());
            if (sdDocType == null) {
                throw new IllegalArgumentException("Could not find struct '" + type.getName() + "'.");
            }
            visit(sdDocType);
            return;
        }

        if (type instanceof MapDataType) {
            MapDataType mType = (MapDataType) type;
            visit(mType.getValueType());
            visit(mType.getKeyType());
        } else if (type instanceof WeightedSetDataType) {
            WeightedSetDataType wType = (WeightedSetDataType) type;
            visit(wType.getNestedType());
        } else if (type instanceof CollectionDataType) {
            CollectionDataType cType = (CollectionDataType) type;
            visit(cType.getNestedType());
        } else if (type instanceof AnnotationReferenceDataType) {
            //do nothing
        } else if (type instanceof PrimitiveDataType) {
            //do nothing
        } else if (type instanceof TensorDataType) {
            //do nothing
        } else if (type instanceof ReferenceDataType) {
            //do nothing
        } else {
            deployLogger.logApplicationPackage(Level.WARNING, "Unknown type : " + type);
        }
    }
}
