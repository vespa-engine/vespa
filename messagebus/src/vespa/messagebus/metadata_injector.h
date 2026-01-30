// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string_view>

namespace mbus {

/**
 * Abstraction for setting metadata values without needing to know anything
 * about how the metadata is being transported.
 */
class MetadataInjector {
public:
    virtual ~MetadataInjector() = default;

    /**
     * Set a particular key/value mapping to be propagated as metadata.
     *
     * Prefer limiting both keys and values to only contain ASCII characters to
     * maximize interoperability with other carrier protocols.
     *
     * It is unspecified whether multiple injection calls with the same key will
     * reflect the first or last value set, so callers should not depend on this.
     *
     * @param key   Metadata key. Should be unique for its purpose.
     * @param value Metadata value.
     */
    virtual void inject_key_value(std::string_view key, std::string_view value) = 0;
};

}
