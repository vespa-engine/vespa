// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.documentapi.ProgressToken;
import com.yahoo.search.result.Hit;
import java.io.IOException;
import java.util.Base64;

/**
 * @deprecated
 */
@Deprecated // OK
// TODO: Remove on Vespa 7
public class ContinuationHit extends Hit {

    private final String value;

    public ContinuationHit(ProgressToken token) {
        super("continuation");
        value = token.serializeToString();
    }

    public static ProgressToken getToken(String continuation) {
        return ProgressToken.fromSerializedString(continuation);
    }

    public String getValue() {
        return value;
    }

}
