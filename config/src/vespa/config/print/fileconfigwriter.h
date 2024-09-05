// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configwriter.h"
#include "configformatter.h"
#include <string>

namespace config {

/**
 * Writes a config to file, optionally using a ConfigFormatter for formatting.
 */
class FileConfigWriter : public ConfigWriter {
public:
    FileConfigWriter(const std::string & fileName);
    // Implements ConfigWriter
    bool write(const ConfigInstance & config) override;
    bool write(const ConfigInstance & config, const ConfigFormatter & formatter) override;
private:
    const std::string _fileName;
};

} // namespace config

