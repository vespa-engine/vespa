// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.KeyId;
import com.yahoo.security.SealedSharedKey;

import java.util.Optional;

/**
 * @author vekterli
 */
public class ToolUtils {

    static void verifyExpectedKeyId(SealedSharedKey sealedSharedKey, Optional<String> maybeKeyId) {
        if (maybeKeyId.isPresent()) {
            var myKeyId = KeyId.ofString(maybeKeyId.get());
            if (!myKeyId.equals(sealedSharedKey.keyId())) {
                // Don't include raw key bytes array verbatim in message (may contain control chars etc.)
                throw new IllegalArgumentException("Key ID specified with --expected-key-id does not match key ID " +
                                                   "used when generating the supplied token");
            }
        }
    }

}
