// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.fieldoperation;

import com.yahoo.schema.document.SDField;

/**
 * An operation on a field. 
 * Operations has a natural order of execution.
 * 
 * @author Einar M R Rosenvinge
 */
public interface FieldOperation extends Comparable<FieldOperation> {

    /** Apply this operation on the given field */
    void apply(SDField field);
    
    @Override
    default int compareTo(FieldOperation other) {
        return 0; // no order by default
    }

}
