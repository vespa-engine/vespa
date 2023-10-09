// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/configgen/configinstance.h>
#include "configformatter.h"

namespace config {

/**
 * Interface for classes capable of writing a config instance somewhere.
 */
class ConfigWriter {
public:
    /**
     * Write this config instance to a place decided by the implementer of this
     * class.
     *
     * @param config The config instance to write.
     */
    virtual bool write(const ConfigInstance & config) = 0;

    /**
     * Write this config instance to a place decided by the implementer of this
     * class. The provided formatter decides the format of the output.
     *
     * @param config The config instance to write.
     * @param formatter The config formatter to use for formatting config.
     */
    virtual bool write(const ConfigInstance & config, const ConfigFormatter & formatter) = 0;
    virtual ~ConfigWriter() { }
};

} // namespace config

