// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sstream>
#include <fstream>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include "configreader.h"

namespace config {

template <typename ConfigType>
class FileConfigReader : public ConfigReader<ConfigType> {
public:
    FileConfigReader(const vespalib::string & fileName);

    // Implements ConfigReader
    std::unique_ptr<ConfigType> read(const ConfigFormatter & formatter);

    /**
     * Read config from this file using old config format.
     *
     * @return An instance of the correct type.
     */
    std::unique_ptr<ConfigType> read();
private:
    const vespalib::string _fileName;
};

} // namespace config

#include "fileconfigreader.hpp"

