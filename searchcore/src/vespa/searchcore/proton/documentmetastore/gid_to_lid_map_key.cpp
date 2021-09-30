// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_map_key.h"
#include <vespa/document/base/globalid.h>

using document::GlobalId;

namespace proton::documentmetastore {

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
