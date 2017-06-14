// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import java.util.Collection;

/**
 * @author baldersheim
 */
public interface DocumentTypeCollection {
    public NewDocumentType getDocumentType(NewDocumentType.Name name);
    public NewDocumentType getDocumentType(int id);
    public Collection<NewDocumentType> getTypes();
}
