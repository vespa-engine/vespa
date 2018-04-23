// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_mapper_factory.h"
#include "gid_to_lid_mapper.h"
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>

namespace proton {

GidToLidMapperFactory::GidToLidMapperFactory(std::shared_ptr<const search::IDocumentMetaStoreContext> dmsContext)
    : _dmsContext(std::move(dmsContext))
{
}

GidToLidMapperFactory::~GidToLidMapperFactory()
{
}

std::unique_ptr<search::IGidToLidMapper>
GidToLidMapperFactory::getMapper() const
{
    return std::make_unique<GidToLidMapper>(*_dmsContext);
}

} // namespace proton
