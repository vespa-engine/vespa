// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>

namespace search { class IDocumentMetaStoreContext; }

namespace proton {

class DocumentMetaStore;

class GidToLidMapperFactory : public search::IGidToLidMapperFactory
{
    std::shared_ptr<const search::IDocumentMetaStoreContext> _dmsContext;
public:
    GidToLidMapperFactory(std::shared_ptr<const search::IDocumentMetaStoreContext> dmsContext);
    virtual ~GidToLidMapperFactory();
    virtual std::unique_ptr<search::IGidToLidMapper> getMapper() const override;
};

} // namespace proton
