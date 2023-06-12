// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.api.annotations.Beta;

import java.util.Set;

/**
 * Information about a field or field set.
 *
 * @author bratseth
 */
@Beta
public interface FieldInfo {

    /** Returns the name of this field or field set. */
    String name();

    Field.Type type();

    /** Returns whether this field or field set is attribute(s), i.e. does indexing: attribute. */
    boolean isAttribute();

    /** Returns whether this field is index(es), i.e. does indexing: index. */
    boolean isIndex();

}
