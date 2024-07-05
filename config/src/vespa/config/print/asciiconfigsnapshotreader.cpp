// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "asciiconfigsnapshotreader.h"
#include "jsonconfigformatter.h"

namespace config {

AsciiConfigSnapshotReader::AsciiConfigSnapshotReader(const vespalib::asciistream & is)
    : _is(is)
{
}

ConfigSnapshot
AsciiConfigSnapshotReader::read()
{
    ConfigDataBuffer buffer;
    buffer.setEncodedString(_is.str());
    JsonConfigFormatter formatter(true);
    formatter.decode(buffer);
    ConfigSnapshot snapshot;
    snapshot.deserialize(buffer);
    return snapshot;
}

} // namespace config
