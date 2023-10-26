// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configsnapshotreader.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

/**
 * Read config snapshots from an ascii stream.
 */
class AsciiConfigSnapshotReader : public ConfigSnapshotReader {
public:
    AsciiConfigSnapshotReader(const vespalib::asciistream & is);

    /**
     * Read a config snapshot.
     *
     * @return Snapshot containing the configs.
     */
    ConfigSnapshot read() override;
private:
    const vespalib::asciistream & _is;
};

} // namespace config

