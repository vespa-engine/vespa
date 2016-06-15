// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import java.util.Collection;

/**
 * @author <a href="mailto:balder@yahoo-inc.com">Henning Baldersheim</a>
 */
public interface DocumentTypeCollection {
    public NewDocumentType getDocumentType(NewDocumentType.Name name);
    public NewDocumentType getDocumentType(int id);
    public Collection<NewDocumentType> getTypes();
}
