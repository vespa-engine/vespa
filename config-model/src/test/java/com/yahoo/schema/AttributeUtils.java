// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDField;

/**
 * Convenience class for tests that need to set attribute properties on fields.
 */
public class AttributeUtils {

    public static void addAttributeAspect(SDField field) {
        field.parseIndexingScript("{ attribute }");
    }

}
