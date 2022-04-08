// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.*;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.TemporarySDDocumentType;

import java.util.*;
import java.util.logging.Level;

/**
 * @author Einar M R Rosenvinge
 */
public class SDDocumentTypeOrderer {

    private final Map<DataTypeName, SDDocumentType> createdSDTypes = new LinkedHashMap<>();
    private final Set<Object> seenTypes = Collections.newSetFromMap(new IdentityHashMap<>());
    List<SDDocumentType> processingOrder = new LinkedList<>();
    private final DeployLogger deployLogger;

    public SDDocumentTypeOrderer(List<SDDocumentType> sdTypes, DeployLogger deployLogger) {
        this.deployLogger = deployLogger;
        for (SDDocumentType type : sdTypes) {
            createdSDTypes.put(type.getDocumentName(), type);
        }
    }

    List<SDDocumentType> getOrdered() { return processingOrder; }

    public void process() {
        for (SDDocumentType type : createdSDTypes.values()) {
            process(type, type);
        }
    }

    private void process(SDDocumentType docOrStruct, SDDocumentType owningDocument) {
        resolveAndProcessInheritedTemporaryTypes(docOrStruct, owningDocument);
        if (seenTypes.contains(docOrStruct)) {
            return;
        }
        seenTypes.add(docOrStruct);
        for (Field field : docOrStruct.fieldSet()) {
            var type = field.getDataType();
            String typeName = type.getName();
            if (!seenTypes.contains(type)) {
                seenTypes.add(type);
                //we haven't seen this before, do it
                visit(type, owningDocument);
            }
        }
        processingOrder.add(docOrStruct);
    }

    private void resolveAndProcessInheritedTemporaryTypes(SDDocumentType type, SDDocumentType owningDocument) {
        List<DataTypeName> toReplace = new ArrayList<>();
        for (SDDocumentType sdoc : type.getInheritedTypes()) {
            if (sdoc instanceof TemporarySDDocumentType) {
                toReplace.add(sdoc.getDocumentName());
            }
        }
        for (DataTypeName name : toReplace) {
            SDDocumentType inherited;
            if (type.isStruct()) {
                inherited = owningDocument.allTypes().get(new NewDocumentType.Name(name.getName()));
                if (inherited == null) throw new IllegalArgumentException("Struct '" + name + "' not found in " + owningDocument);
                process(inherited, owningDocument);
            }
            else {
                inherited = createdSDTypes.get(name);
                if (inherited == null) {
                    throw new IllegalArgumentException("document " + type.getName() +
                                                       " inherits from unavailable document " + name);
                }
                process(inherited, inherited);
            }
            type.inherit(inherited);
        }
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

    private void visit(DataType type, SDDocumentType owningDocument) {
        if (type instanceof StructuredDataType) {
            StructuredDataType structType = (StructuredDataType) type;
            SDDocumentType sdDocType = owningDocument.getType(structType.getName());
            if (sdDocType == null) {
                sdDocType = find(structType.getName());
            }
            if (sdDocType == null) {
                throw new IllegalArgumentException("Could not find struct '" + type.getName() + "'");
            }
            process(sdDocType, owningDocument);
            return;
        }

        if (type instanceof MapDataType) {
            MapDataType mType = (MapDataType) type;
            visit(mType.getValueType(), owningDocument);
            visit(mType.getKeyType(), owningDocument);
        } else if (type instanceof WeightedSetDataType) {
            WeightedSetDataType wType = (WeightedSetDataType) type;
            visit(wType.getNestedType(), owningDocument);
        } else if (type instanceof CollectionDataType) {
            CollectionDataType cType = (CollectionDataType) type;
            visit(cType.getNestedType(), owningDocument);
        } else if (type instanceof AnnotationReferenceDataType) {
            //do nothing
        } else if (type instanceof PrimitiveDataType) {
            //do nothing
        } else if (type instanceof TensorDataType) {
            //do nothing
        } else if (type instanceof NewDocumentReferenceDataType) {
            //do nothing
        } else {
            deployLogger.logApplicationPackage(Level.WARNING, "Unknown type : " + type);
        }
    }

}
