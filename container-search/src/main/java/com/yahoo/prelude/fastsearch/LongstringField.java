// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a long string field in the result set.
 *
 */
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;

/**
 * @author Bjørn Borud
 */
public class LongstringField extends DocsumField {

    public LongstringField(String name) {
        super(name);
    }

    @Override
    public Object convert(Inspector value) {
        return value.asString("");
    }

}
