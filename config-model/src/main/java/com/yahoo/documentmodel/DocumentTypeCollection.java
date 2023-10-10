// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import java.util.Collection;

/**
 * @author baldersheim
 */
public interface DocumentTypeCollection {

    NewDocumentType getDocumentType(NewDocumentType.Name name);
    NewDocumentType getDocumentType(int id);
    Collection<NewDocumentType> getTypes();

}
