// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author baldersheim
 */
public class DocumentTypeRepo implements DocumentTypeCollection {

    private final Map<Integer, NewDocumentType> typeById = new LinkedHashMap<>();
    private final Map<NewDocumentType.Name, NewDocumentType> typeByName = new LinkedHashMap<>();

    public final NewDocumentType getDocumentType(String name) {
        return typeByName.get(new NewDocumentType.Name(name));
    }
    public NewDocumentType getDocumentType(NewDocumentType.Name name) {
        return typeByName.get(name);
    }

    public NewDocumentType getDocumentType(int id) {
        return typeById.get(id);
    }

    public Collection<NewDocumentType> getTypes() { return typeById.values(); }

    public DocumentTypeRepo add(NewDocumentType type) {
        if (typeByName.containsKey(type.getFullName())) {
            throw new IllegalArgumentException("Document type " + type + " is already registered");
        }
        if (typeById.containsKey(type.getFullName().getId())) {
            throw new IllegalArgumentException("Document type " + type + " is already registered");
        }
        typeByName.put(type.getFullName(), type);
        typeById.put(type.getFullName().getId(), type);
        return this;
    }

}
