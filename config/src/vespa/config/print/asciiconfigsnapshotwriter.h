// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configsnapshotwriter.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

/**
 * Write a config snapshot to an ascii stream.
 */
class AsciiConfigSnapshotWriter : public ConfigSnapshotWriter {
public:
    AsciiConfigSnapshotWriter(vespalib::asciistream & os);
    bool write(const ConfigSnapshot & snapshot) override;
private:
    vespalib::asciistream & _os;
};

} // namespace config

