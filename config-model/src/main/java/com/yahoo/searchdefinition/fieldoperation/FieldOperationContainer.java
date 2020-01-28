// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

/**
 * @author Einar M R Rosenvinge
 */
public interface FieldOperationContainer {

    /** Adds an operation */
    void addOperation(FieldOperation op);

    /** Apply all operations. Operations must be sorted in their natural order before applying each operation. */
    void applyOperations(SDField field);

    String getName();

}
