// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <fstream>
#include <sstream>
#include "fileconfigsnapshotreader.h"
#include "jsonconfigformatter.h"
#include <iostream>

namespace config {

FileConfigSnapshotReader::FileConfigSnapshotReader(const vespalib::string & fileName)
    : _fileName(fileName)
{
}

ConfigSnapshot
FileConfigSnapshotReader::read()
{
    std::ifstream file(_fileName.c_str());
    if (!file.is_open())
        throw ConfigReadException("error: unable to read file '%s'", _fileName.c_str());

    std::stringstream buf;
    buf << file.rdbuf();

    ConfigDataBuffer buffer;
    buffer.setEncodedString(buf.str());
    JsonConfigFormatter formatter(true);
    formatter.decode(buffer);
    ConfigSnapshot snapshot;
    snapshot.deserialize(buffer);
    return snapshot;
}

} // namespace config
