// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DocumentType;
import com.yahoo.document.StructDataType;

/**
 * Model for StructDataType declared in a specific document
 *
 * @author arnej
 **/
public final class OwnedStructDataType extends StructDataType implements OwnedType {

    private final String ownerName;
    private final String uniqueName;
    private boolean overrideId = false;

    public OwnedStructDataType(String name, DocumentType document) {
        this(name, document.getName());
    }

    public OwnedStructDataType(String name, String owner) {
        super(name);
        this.ownerName = owner;
        this.uniqueName = name + "@" + owner;
    }

    public void enableOverride() {
        this.overrideId = true;
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
    public String getName() {
        return overrideId ? uniqueName : super.getName();
    }

    @Override
    public int getId() {
        return overrideId ? getUniqueId() : super.getId();
    }

    @Override
    public String toString() {
        return "{OwnedStructDataType "+uniqueName+" id="+getId()+" uid="+getUniqueId()+" enable override="+overrideId+"}";
    }
}
