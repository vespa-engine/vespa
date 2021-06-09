package com.yahoo.searchdefinition;

import java.nio.ByteBuffer;

public class RankExpressionBody extends DistributableResource {

    public RankExpressionBody(String name, ByteBuffer body) {
        super(name, body);
    }
}
