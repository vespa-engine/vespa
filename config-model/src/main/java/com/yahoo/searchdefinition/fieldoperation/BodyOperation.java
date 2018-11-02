// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class BodyOperation implements FieldOperation {
    @SuppressWarnings("deprecation")  // TODO Vespa 7: remove annotation and fix usage of field.setHeader
    public void apply(SDField field) {
        field.setHeader(false);
        field.setHeaderOrBodyDefined(true);
    }
}
