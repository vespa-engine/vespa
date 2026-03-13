// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <string_view>
#include <optional>

namespace mbus {

/**
 * Abstraction for getting metadata values without needing to know anything
 * about how the metadata is being transported.
 */
class MetadataExtractor {
public:
    virtual ~MetadataExtractor() = default;

    /**
     * Retrieves a metadata value for a particular key, returning an empty optional
     * if there is no mapped value for the key.
     *
     * @param key metadata-specific key. Should be unique for its purpose.
     * @return the metadata value the key resolves to, or an empty optional if no value existed.
     */
    [[nodiscard]] virtual std::optional<std::string> extract_value(std::string_view key) const = 0;
};

}
