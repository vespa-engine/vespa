package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDField;

/**
 * Convenience class for tests that need to set attribute properties on fields.
 */
public class AttributeUtils {

    public static void addAttributeAspect(SDField field) {
        field.parseIndexingScript("{ attribute }");
    }

}
