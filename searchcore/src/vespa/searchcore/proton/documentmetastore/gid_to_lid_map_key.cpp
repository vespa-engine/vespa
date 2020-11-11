// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_map_key.h"
#include <vespa/document/base/globalid.h>

using document::GlobalId;

namespace proton::documentmetastore {

GidToLidMapKey::GidToLidMapKey()
    : _lid(FIND_DOC_ID),
      _gid_key(0u)
{
}

GidToLidMapKey::GidToLidMapKey(uint32_t lid, uint32_t gid_key)
    : _lid(lid),
      _gid_key(gid_key)
{
}

GidToLidMapKey::GidToLidMapKey(uint32_t lid, const GlobalId& gid)
    : GidToLidMapKey(lid, GlobalId::BucketOrderCmp::gid_key32(gid))
{
}

GidToLidMapKey
GidToLidMapKey::make_find_key(const GlobalId& gid)
{
    return GidToLidMapKey(FIND_DOC_ID, gid);
}

}
