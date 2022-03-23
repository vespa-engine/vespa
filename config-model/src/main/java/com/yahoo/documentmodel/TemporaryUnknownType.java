// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.StructDataType;

/**
 * Proxy for an unknown type (must resolve to struct or document)
 *
 * @author arnej
 **/
public final class TemporaryUnknownType extends StructDataType {

    public TemporaryUnknownType(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "{TemporaryUnknownType "+getName()+" id="+getId()+"}";
    }
}
