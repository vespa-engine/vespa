// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configformatter.h"

namespace config {

/**
 * Interface implemented by all classes capable of reading config of a specific
 * type.
 */
template <typename ConfigType>
class ConfigReader {
public:
    /**
     * Read a config using a provided formatter, and return the correct type.
     *
     * @param formatter Something implementing ConfigFormatter interface.
     * @return Instance of correct type.
     */
    virtual std::unique_ptr<ConfigType> read(const ConfigFormatter & formatter) = 0;
    virtual ~ConfigReader() { }
};

} // namespace config

