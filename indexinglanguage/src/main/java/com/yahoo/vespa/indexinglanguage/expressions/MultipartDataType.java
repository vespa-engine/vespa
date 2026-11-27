// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The type of a multipart value: An array of parts, as produced by 'expression1, expression2'.
 * This type is an {@code array<any>} which also holds the type of each part.
 * An expression can validate the parts from these types when a script is verified.
 * Only the type resolution of indexing scripts needs this type, so this type is in indexinglanguage, not in document.
 *
 * @author glebashnik
 */
final class MultipartDataType extends ArrayDataType {

    private final List<DataType> partTypes;

    MultipartDataType(List<DataType> partTypes) {
        super(AnyDataType.instance);
        this.partTypes = Collections.unmodifiableList(new ArrayList<>(partTypes));
    }

    /** A type which is not known is null. */
    List<DataType> partTypes() { return partTypes; }

}
