// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.annotation.SpanTree;

/**
 * @author Einar M R Rosenvinge
 */
public interface SpanTreeReader {
    public void read(SpanTree tree);
}
