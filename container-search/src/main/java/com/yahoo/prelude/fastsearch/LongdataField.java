// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.prelude.hitfield.RawData;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.Inspector;

/**
 * @author Bjørn Borud
 */
public class LongdataField extends DocsumField {

    public LongdataField(String name) {
        super(name);
    }

    private Object convert(byte[] value) {
        return new RawData(value);
    }

    @Override
    public Object convert(Inspector value) {
        return convert(value.asData(Value.empty().asData()));
    }

}
