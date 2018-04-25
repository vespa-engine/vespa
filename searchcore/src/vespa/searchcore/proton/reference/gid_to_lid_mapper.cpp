// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_mapper.h"
#include <vespa/searchlib/common/idocumentmetastore.h>

namespace proton {

GidToLidMapper::GidToLidMapper(const search::IDocumentMetaStoreContext &dmsContext)
    : _guard(dmsContext.getReadGuard())
{
}

GidToLidMapper::~GidToLidMapper()
{
}

void
GidToLidMapper::foreach(const search::IGidToLidMapperVisitor &visitor) const
{
    _guard->get().foreach(visitor);
}


} // namespace proton
