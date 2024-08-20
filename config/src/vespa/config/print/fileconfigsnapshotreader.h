// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configsnapshotreader.h"
#include <string>

namespace config {

/**
 * Read config snapshots from file.
 */
class FileConfigSnapshotReader : public ConfigSnapshotReader {
public:
    FileConfigSnapshotReader(const std::string & fileName);

    /**
     * Read a config snapshot.
     *
     * @return Snapshot containing the configs.
     */
    ConfigSnapshot read() override;
private:
    const std::string _fileName;
};

} // namespace config

