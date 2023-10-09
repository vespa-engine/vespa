// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "asciiconfigwriter.h"
#include "fileconfigformatter.h"

namespace config {

AsciiConfigWriter::AsciiConfigWriter(vespalib::asciistream & os)
    : _os(os)
{
}

bool
AsciiConfigWriter::write(const ConfigInstance & config)
{
    return write(config, FileConfigFormatter());
}

bool
AsciiConfigWriter::write(const ConfigInstance & config, const ConfigFormatter & formatter)
{
    ConfigDataBuffer buffer;
    config.serialize(buffer);
    formatter.encode(buffer);
    _os << buffer.getEncodedString();
    return !_os.fail();
}

} // namespace config
