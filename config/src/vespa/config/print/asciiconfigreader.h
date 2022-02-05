// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configreader.h"

namespace config {

class ConfigFormatter;

/**
 * Read a config from istream
 */
template <typename ConfigType>
class AsciiConfigReader : public ConfigReader<ConfigType>
{
public:
    AsciiConfigReader(vespalib::asciistream & is);
    std::unique_ptr<ConfigType> read();
    std::unique_ptr<ConfigType> read(const ConfigFormatter & formatter) override;
private:
    vespalib::asciistream & _is;
};

} // namespace config
