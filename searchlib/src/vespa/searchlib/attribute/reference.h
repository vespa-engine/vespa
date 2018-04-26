// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/datastore/entryref.h>

namespace search::attribute {

/*
 * Class representing a single reference in a reference attribute.
 */
class Reference {
    using EntryRef = search::datastore::EntryRef;
    using GlobalId = document::GlobalId;
    GlobalId _gid;
    mutable uint32_t _lid;  // target lid
    mutable EntryRef _revMapIdx; // map from gid to lids referencing gid
public:
    Reference()
        : _gid(),
          _lid(0u),
          _revMapIdx()
    {
    }
    Reference(const GlobalId &gid_)
        : _gid(gid_),
          _lid(0u),
          _revMapIdx()
    {
    }
    bool operator<(const Reference &rhs) const {
        return _gid < rhs._gid;
    }
    const GlobalId &gid() const { return _gid; }
    uint32_t lid() const { return _lid; }
    EntryRef revMapIdx() const { return _revMapIdx; }
    void setLid(uint32_t targetLid) const { _lid = targetLid; }
    void setRevMapIdx(EntryRef newRevMapIdx) const { _revMapIdx = newRevMapIdx; }
};

}
