// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/asciistream.h>
#include "configreader.h"
#include "configformatter.h"

namespace config {

/**
 * Read a config from istream
 */
template <typename ConfigType>
class AsciiConfigReader : public ConfigReader<ConfigType>
{
public:
    AsciiConfigReader(vespalib::asciistream & is);
    std::unique_ptr<ConfigType> read();
    std::unique_ptr<ConfigType> read(const ConfigFormatter & formatter);
private:
    vespalib::asciistream & _is;
};

} // namespace config

#include "asciiconfigreader.hpp"

