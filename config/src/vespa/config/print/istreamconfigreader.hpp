// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <istream>

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
    return std::unique_ptr<ConfigType>(new ConfigType(buffer));
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
IstreamConfigReader<ConfigType>::read()
{
    std::vector<vespalib::string> lines;
    std::string line;
    while (getline(_is, line)) {
        lines.push_back(line);
    }
    return std::unique_ptr<ConfigType>(new ConfigType(ConfigValue(lines, calculateContentXxhash64(lines))));
}

} // namespace config
