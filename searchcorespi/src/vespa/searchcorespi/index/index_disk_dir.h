// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace searchcorespi::index {

/*
 * Class naming a disk index for a document type.
 */
class IndexDiskDir {
    uint32_t _id;
    bool     _fusion;
public:
    IndexDiskDir(uint32_t id, bool fusion) noexcept
        : _id(id),
          _fusion(fusion)
    {
    }
    IndexDiskDir() noexcept
        : IndexDiskDir(0, false)
    {
    }
    bool operator<(const IndexDiskDir& rhs) const noexcept {
        if (_id != rhs._id) {
            return _id < rhs._id;
        }
        return !_fusion && rhs._fusion;
    }
    bool operator==(const IndexDiskDir& rhs) const noexcept {
        return (_id == rhs._id) && (_fusion == rhs._fusion);
    }
    bool valid() const noexcept { return _id != 0u; }
    bool is_fusion_index() const noexcept { return _fusion; }
    uint64_t get_id() const noexcept { return _id; }
};

}
