// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.documentapi.ProgressToken;
import com.yahoo.search.result.Hit;
import java.io.IOException;
import java.util.Base64;

public class ContinuationHit extends Hit {

    private final String value;

    public ContinuationHit(ProgressToken token) {
        super("continuation");

        final byte[] serialized = token.serialize();
        value = Base64.getUrlEncoder().encodeToString(serialized);
    }

    public static ProgressToken getToken(String continuation) throws IOException {
        byte[] serialized;
        try {
            serialized = Base64.getUrlDecoder().decode(continuation);
        } catch (IllegalArgumentException e) {
            // Legacy visitor tokens were encoded with MIME Base64 which may fail decoding as URL-safe.
            // Try again with MIME decoder to avoid breaking upgrade scenarios.
            // TODO(vekterli): remove once this is no longer a risk.
            serialized = Base64.getMimeDecoder().decode(continuation);
        }
        return new ProgressToken(serialized);
    }

    public String getValue() {
        return value;
    }

}
