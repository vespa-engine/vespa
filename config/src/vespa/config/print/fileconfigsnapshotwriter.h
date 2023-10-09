// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configsnapshotwriter.h"
#include <vespa/vespalib/stllike/string.h>

namespace config {

/**
 * Write a config snapshot to a file.
 */
class FileConfigSnapshotWriter : public ConfigSnapshotWriter {
public:
    FileConfigSnapshotWriter(const vespalib::string & fileName);
    bool write(const ConfigSnapshot & snapshot) override;
private:
    const vespalib::string _fileName;
};

} // namespace config

