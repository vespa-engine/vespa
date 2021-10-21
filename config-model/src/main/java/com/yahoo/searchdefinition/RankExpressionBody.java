// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import java.nio.ByteBuffer;

public class RankExpressionBody extends DistributableResource {

    public RankExpressionBody(String name, ByteBuffer body) {
        super(name, body);
    }
}
