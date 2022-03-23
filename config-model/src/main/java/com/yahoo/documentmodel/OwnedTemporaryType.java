// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DocumentType;
import com.yahoo.document.StructDataType;

/**
 * Proxy for a struct type declared in a specific document
 *
 * @author arnej
 **/
public final class OwnedTemporaryType extends StructDataType implements OwnedType {

    private final String ownerName;
    private final String uniqueName;

    public OwnedTemporaryType(String name, DocumentType document) {
        this(name, document.getName());
    }

    public OwnedTemporaryType(String name, String owner) {
        super(name);
        this.ownerName = owner;
        this.uniqueName = name + "@" + owner;
    }

    @Override
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public String getUniqueName() {
        return uniqueName;
    }

    @Override
    public String toString() {
        return "{OwnedTemporaryType "+uniqueName+" id="+getId()+" uid="+getUniqueId()+"}";
    }
}
