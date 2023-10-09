// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/retriever/configsnapshot.h>

namespace config {

/**
 * Interface implemented by all classes capable of reading config of a specific
 * type.
 */
class ConfigSnapshotReader {
public:
    /**
     * Read a config snapshot.
     *
     * @return Snapshot containing the configs.
     */
    virtual ConfigSnapshot read() = 0;
    virtual ~ConfigSnapshotReader() { }
};

} // namespace config

