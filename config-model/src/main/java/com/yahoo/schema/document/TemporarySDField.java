// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.DataType;

/**
 * @author Einar M R Rosenvinge
 */
public class TemporarySDField extends SDField {

    public TemporarySDField(SDDocumentType repo, String name, DataType dataType, SDDocumentType owner) {
        super(repo, name, dataType, owner);
    }

    public TemporarySDField(SDDocumentType repo, String name, DataType dataType) {
        super(repo, name, dataType);
    }

}
