// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MemorySnapshotList
 *
 * \brief Holds a historic list of MemoryStates.
 *
 */

#pragma once

#include <storage/frameworkimpl/memory/memorystate.h>

namespace storage {

class MemorySnapshotList : public vespalib::Printable {
    std::map<uint64_t time, MemoryState> _snapshots;
};

} // storage

