// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileconfigreader.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <fstream>
#include <sstream>

namespace config {

template <typename ConfigType>
FileConfigReader<ConfigType>::FileConfigReader(const vespalib::string & fileName)
    : _fileName(fileName)
{
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
FileConfigReader<ConfigType>::read(const ConfigFormatter & formatter)
{
    ConfigDataBuffer buffer;
    std::ifstream file(_fileName.c_str());
    if (!file.is_open())
        throw ConfigReadException("error: unable to read file '%s'", _fileName.c_str());

    std::stringstream buf;
    buf << file.rdbuf();
    buffer.setEncodedString(buf.str());
    formatter.decode(buffer);
    return std::make_unique<ConfigType>(buffer);
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
FileConfigReader<ConfigType>::read()
{
    StringVector lines;
    std::ifstream f(_fileName.c_str());
    if (f.fail())
        throw vespalib::IllegalArgumentException(std::string("Unable to open file ") + _fileName);
    std::string line;
    for (std::getline(f, line); f; std::getline(f, line)) {
        lines.push_back(line);
    }
    return std::make_unique<ConfigType>(ConfigValue(std::move(lines)));
}

} // namespace config
