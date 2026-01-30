// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.api.annotations.Beta;

import java.util.Optional;

/**
 * Abstraction for getting metadata values without needing to know anything
 * about how the metadata is being transported.
 */
@Beta
public interface MetadataExtractor {

    /**
     * Retrieves a metadata value for a particular key, returning <code>Optional.empty()</code>
     * if there is no mapped value for the key.
     *
     * @param key metadata-specific key. Should be unique for its purpose.
     * @return the metadata value the key resolves to, or an empty Optional if no value existed.
     */
    Optional<String> extractValue(String key);

}
