// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <limits>

namespace document { class GlobalId; }

namespace proton::documentmetastore {

/*
 * Class containing lid and the most significant portion of gid according
 * to compare functor (document::GlobalId::BucketOrderCmp).
 */
class GidToLidMapKey {
    uint32_t _lid;
    uint32_t _gid_key;
    static constexpr uint32_t FIND_DOC_ID = std::numeric_limits<uint32_t>::max();

public:
    GidToLidMapKey() noexcept
        : _lid(FIND_DOC_ID),
          _gid_key(0u)
    {
    }
    GidToLidMapKey(uint32_t lid, uint32_t gid_key) noexcept
        : _lid(lid),
          _gid_key(gid_key)
    {
    }
    GidToLidMapKey(uint32_t lid, const document::GlobalId &gid);
    static GidToLidMapKey make_find_key(const document::GlobalId &gid);

    uint32_t get_lid() const { return _lid; }
    uint32_t get_gid_key() const { return _gid_key; }
    bool is_find_key() const { return _lid == FIND_DOC_ID; }
};

}
