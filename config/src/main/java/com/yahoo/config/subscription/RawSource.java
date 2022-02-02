// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

/**
 * Source specifying raw config, where payload is given programmatically
 *
 * @author Vegard Havdal
 * @deprecated  Will be removed in Vespa 8. Only for internal use.
 */
@Deprecated(forRemoval = true, since = "7")
public class RawSource implements ConfigSource {

    public final String payload;

    /**
     * New source with the given payload on Vespa cfg format
     * @param payload config payload
     */
    public RawSource(String payload) {
        this.payload = payload;
    }

}
