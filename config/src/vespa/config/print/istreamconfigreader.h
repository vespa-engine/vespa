// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configreader.h"
#include "configformatter.h"

namespace config {

/**
 * Read a config from istream
 */
template <typename ConfigType>
class IstreamConfigReader : public ConfigReader<ConfigType>
{
public:
    IstreamConfigReader(std::istream & is);
    std::unique_ptr<ConfigType> read();
    std::unique_ptr<ConfigType> read(const ConfigFormatter & formatter) override;
private:
    std::istream & _is;
};

} // namespace config
