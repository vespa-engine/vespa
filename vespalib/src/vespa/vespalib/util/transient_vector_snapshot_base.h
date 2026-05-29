// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/*
 * Base class for templated child classes that contains transient snapshots of vectors.
 */
class TransientVectorSnapshotBase {
public:
    TransientVectorSnapshotBase();
    ~TransientVectorSnapshotBase();
};

} // namespace vespalib
