// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * Flags for custom string encodings.  For instance, a flag can be of the type java.time.Duration,
 * and it can be overridden with a JSON string parsed via Duration::parse.  Any failure to parse
 * the JSON string will prevent the override from being set.
 *
 * @author Håkon Hallingstad
 */
public class UnboundCodecFlag<T> extends UnboundFlagImpl<T, CodecFlag<T>, UnboundCodecFlag<T>> {

    public UnboundCodecFlag(FlagId id, T defaultValue, FetchVector defaultFetchVector, FlagSerializer<T> serializer) {
        super(id,
              defaultValue,
              defaultFetchVector,
              serializer,
              (id_, defaultValue_, fetchVector_) -> new UnboundCodecFlag<>(id_, defaultValue_, fetchVector_, serializer),
              CodecFlag::new);
    }
}
