// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.api.annotations.Beta;

/**
 * Abstraction for setting metadata values without needing to know anything
 * about how the metadata is being transported.
 */
@Beta
public interface MetadataInjector {

    /**
     * <p>Set a particular key/value mapping to be propagated as metadata.</p>
     *
     * <p>Prefer limiting both keys and values to only contain ASCII characters to
     * maximize interoperability with other carrier protocols.</p>
     *
     * <p>It is unspecified whether multiple injection calls with the same key will
     * reflect the first or last value set, so callers should not depend on this.</p>
     *
     * @param key   Metadata key. Should be unique for its purpose.
     * @param value Metadata value.
     */
    void injectKeyValue(String key, String value);

}
