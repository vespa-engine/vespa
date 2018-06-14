// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compact_lid_space_operation.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace proton {

CompactLidSpaceOperation::CompactLidSpaceOperation()
    : FeedOperation(FeedOperation::COMPACT_LID_SPACE),
      _subDbId(0),
      _lidLimit(0)
{
}

CompactLidSpaceOperation::CompactLidSpaceOperation(uint32_t subDbId, uint32_t lidLimit)
    : FeedOperation(FeedOperation::COMPACT_LID_SPACE),
      _subDbId(subDbId),
      _lidLimit(lidLimit)
{
}

void
CompactLidSpaceOperation::serialize(vespalib::nbostream& os) const
{
    os << _subDbId;
    os << _lidLimit;
}

void
CompactLidSpaceOperation::deserialize(vespalib::nbostream& is, const document::DocumentTypeRepo&)
{
    is >> _subDbId;
    is >> _lidLimit;
}

vespalib::string
CompactLidSpaceOperation::toString() const
{
    return vespalib::make_string("CompactLidSpace(subDbId=%u, lidLimit=%u, serialNum=%" PRIu64 ")",
            _subDbId, _lidLimit, getSerialNum());
}

}
