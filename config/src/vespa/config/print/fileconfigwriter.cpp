// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <fstream>
#include "fileconfigwriter.h"
#include "fileconfigformatter.h"
#include "ostreamconfigwriter.h"
#include <vespa/config/common/exceptions.h>

namespace config {

FileConfigWriter::FileConfigWriter(const vespalib::string & fileName)
    : _fileName(fileName)
{
}

bool
FileConfigWriter::write(const ConfigInstance & config)
{
    return write(config, FileConfigFormatter());
}

bool
FileConfigWriter::write(const ConfigInstance & config, const ConfigFormatter & formatter)
{
    std::ofstream file(_fileName.c_str());
    if (!file.is_open())
        throw ConfigWriteException("error: could not open output file: '%s'\n", _fileName.c_str());
    OstreamConfigWriter osw(file);
    return osw.write(config, formatter);
}

} // namespace config
