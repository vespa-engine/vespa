// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.document.DataType;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class TemporarySDField extends SDField {

    public TemporarySDField(String name, DataType dataType, SDDocumentType owner) {
        super(owner, name, dataType, owner, false);
    }

    public TemporarySDField(String name, DataType dataType) {
        super(null, name, dataType, false);
    }

}
