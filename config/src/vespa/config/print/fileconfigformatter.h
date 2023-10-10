// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configformatter.h"

namespace config {

/**
 * Formatter capable of encoding config into old config format. Decoding is not
 * supported.
 */
class FileConfigFormatter : public ConfigFormatter {
public:
    // Inherits ConfigFormatter
    void encode(ConfigDataBuffer & buffer) const override;
    // Inherits ConfigFormatter
    size_t decode(ConfigDataBuffer & buffer) const override;
};

} // namespace config

