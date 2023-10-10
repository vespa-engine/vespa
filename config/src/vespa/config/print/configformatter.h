// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configdatabuffer.h"

namespace config {

/**
 * Interface used by all config formatters. A config formatter is capable of
 * encoding and decoding into any kind of format that can be put into a string.
 */
class ConfigFormatter {
public:
    /**
     * Encode the slime object in a config data buffer, and put it into its
     * string.
     *
     * @param buffer A ConfigDataBuffer containing a slime object that should be
     *               encoded.
     */
    virtual void encode(ConfigDataBuffer & buffer) const = 0;

    /**
     * Decode a string in the config data buffer and populate its slime object.
     *
     * @param buffer A ConfigDataBuffer containing a string of the config.
     */
    virtual size_t decode(ConfigDataBuffer & buffer) const = 0;

    virtual ~ConfigFormatter() { }
};

} // namespace config

