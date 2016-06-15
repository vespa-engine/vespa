// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "configwriter.h"
#include "configformatter.h"

namespace config {

/**
 * Writes a config to file, optionally using a ConfigFormatter for formatting.
 */
class FileConfigWriter : public ConfigWriter {
public:
    FileConfigWriter(const vespalib::string & fileName);
    // Implements ConfigWriter
    bool write(const ConfigInstance & config);
    bool write(const ConfigInstance & config, const ConfigFormatter & formatter);
private:
    const vespalib::string _fileName;
};

} // namespace config

