// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    return std::unique_ptr<ConfigType>(new ConfigType(buffer));
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
AsciiConfigReader<ConfigType>::read()
{
    std::vector<vespalib::string> lines;
    vespalib::string line;
    while (getline(_is, line)) {
        lines.push_back(line);
    }
    return std::unique_ptr<ConfigType>(new ConfigType(ConfigValue(lines, calculateContentXxhash64(lines))));
}

} // namespace config
