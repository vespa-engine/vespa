// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/retriever/configsnapshot.h>

namespace config {

/**
 * Interface for classes capable of writing a config snapshots somewhere.
 */
class ConfigSnapshotWriter {
public:
    /**
     * Write this config snapshot to a place decided by the implementer of this
     * class.
     *
     * @param config The config snapshot to write.
     * @return true if successful, false if not.
     */
    virtual bool write(const ConfigSnapshot & snapshot) = 0;
    virtual ~ConfigSnapshotWriter() { }
};

} // namespace config

