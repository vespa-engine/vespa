// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {

/*
 * Class representing the relative naming of a underlying file for a
 * data store.
 */
class DataStoreFileChunkId
{
    uint64_t _nameId;
public:
    DataStoreFileChunkId(uint64_t nameId_in)
        : _nameId(nameId_in)
    {
    }
    uint64_t nameId() const               { return _nameId; }
    vespalib::string createName(const vespalib::string &baseName) const;
    bool operator<(const DataStoreFileChunkId &rhs) const {
        return _nameId < rhs._nameId;
    }
};

} // namespace search
