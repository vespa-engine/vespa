// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileconfigsnapshotwriter.h"
#include "jsonconfigformatter.h"
#include <vespa/config/common/exceptions.h>
#include <fstream>

namespace config {

FileConfigSnapshotWriter::FileConfigSnapshotWriter(const vespalib::string & fileName)
    : _fileName(fileName)
{
}

bool
FileConfigSnapshotWriter::write(const ConfigSnapshot & snapshot)
{
    std::ofstream file(_fileName.c_str());
    if (!file.is_open())
        throw ConfigWriteException("error: could not open output file '%s'\n", _fileName.c_str());

    ConfigDataBuffer buffer;
    snapshot.serialize(buffer);
    JsonConfigFormatter formatter(true);
    formatter.encode(buffer);
    file << buffer.getEncodedString();
    return !file.fail();
}

} // namespace config
