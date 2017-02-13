// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "gid_to_lid_mapper.h"

namespace proton {

GidToLidMapper::GidToLidMapper(vespalib::GenerationHandler::Guard &&guard,
                               const search::IDocumentMetaStore &dms)
    : _guard(std::move(guard)),
      _dms(dms)
{
}

GidToLidMapper::~GidToLidMapper()
{
}

uint32_t
GidToLidMapper::mapGidToLid(const document::GlobalId &gid) const
{
    uint32_t lid = 0;
    if (_dms.getLid(gid, lid)) {
        return lid;
    } else {
        return 0u;
    }
}

} // namespace search
