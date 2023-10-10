// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "asciiconfigsnapshotwriter.h"
#include "jsonconfigformatter.h"

namespace config {

AsciiConfigSnapshotWriter::AsciiConfigSnapshotWriter(vespalib::asciistream & os)
    : _os(os)
{
}

bool
AsciiConfigSnapshotWriter::write(const ConfigSnapshot & snapshot)
{
    ConfigDataBuffer buffer;
    snapshot.serialize(buffer);
    JsonConfigFormatter formatter(true);
    formatter.encode(buffer);
    _os << buffer.getEncodedString();
    return !_os.fail();
}

} // namespace config
