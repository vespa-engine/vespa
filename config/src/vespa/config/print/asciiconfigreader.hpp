// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "asciiconfigreader.h"
#include <vespa/config/common/types.h>
#include <vespa/config/common/configvalue.h>

namespace config {

template <typename ConfigType>
AsciiConfigReader<ConfigType>::AsciiConfigReader(vespalib::asciistream & is)
    : _is(is)
{
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
AsciiConfigReader<ConfigType>::read(const ConfigFormatter & formatter)
{
    ConfigDataBuffer buffer;
    buffer.setEncodedString(_is.str());
    formatter.decode(buffer);
    return std::make_unique<ConfigType>(buffer);
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
AsciiConfigReader<ConfigType>::read()
{
    StringVector lines;
    vespalib::string line;
    while (getline(_is, line)) {
        lines.push_back(line);
    }
    return std::make_unique<ConfigType>(ConfigValue(std::move(lines)));
}

} // namespace config
