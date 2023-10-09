// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ostreamconfigwriter.h"
#include "fileconfigformatter.h"
#include <ostream>

namespace config {

OstreamConfigWriter::OstreamConfigWriter(std::ostream & os)
    : _os(os)
{
}

bool
OstreamConfigWriter::write(const ConfigInstance & config, const ConfigFormatter & formatter)
{
    ConfigDataBuffer buffer;
    config.serialize(buffer);
    formatter.encode(buffer);
    _os << buffer.getEncodedString();
    return !_os.fail();
}

bool
OstreamConfigWriter::write(const ConfigInstance & config)
{
    return write(config, FileConfigFormatter());
}

} // namespace config
