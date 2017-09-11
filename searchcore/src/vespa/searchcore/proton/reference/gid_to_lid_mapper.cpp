// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_mapper.h"
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>

namespace proton {

GidToLidMapper::GidToLidMapper(vespalib::GenerationHandler::Guard &&guard,
                               const DocumentMetaStore &dms)
    : _guard(std::move(guard)),
      _dms(dms)
{
}

GidToLidMapper::~GidToLidMapper()
{
}

void
GidToLidMapper::foreach(const search::IGidToLidMapperVisitor &visitor) const
{
    const auto &dms = _dms;
    dms.beginFrozen().foreach_key([&dms,&visitor](uint32_t lid)
                                  {  visitor.visit(dms.getRawMetaData(lid).getGid(), lid); });
}


} // namespace proton
