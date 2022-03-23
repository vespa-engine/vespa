// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

/**
 * API for a type declared in a specific document
 *
 * @author arnej
 **/
public interface OwnedType {
    String getOwnerName();
    String getUniqueName();
    default int getUniqueId() {
        return getUniqueName().hashCode();
    }
}
