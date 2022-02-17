// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "istreamconfigreader.h"
#include <sstream>

namespace config {

template <typename ConfigType>
IstreamConfigReader<ConfigType>::IstreamConfigReader(std::istream & is)
    : _is(is)
{
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
IstreamConfigReader<ConfigType>::read(const ConfigFormatter & formatter)
{
    ConfigDataBuffer buffer;
    std::stringstream buf;
    buf << _is.rdbuf();
    buffer.setEncodedString(buf.str());
    formatter.decode(buffer);
    return std::make_unique<ConfigType>(buffer);
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
IstreamConfigReader<ConfigType>::read()
{
    StringVector lines;
    std::string line;
    while (getline(_is, line)) {
        lines.push_back(line);
    }
    return std::make_unique<ConfigType>(ConfigValue(std::move(lines)));
}

} // namespace config
