// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>

namespace proton {

class GidToLidMapperFactory : public search::IGidToLidMapperFactory
{
    std::shared_ptr<DocumentMetaStore> _dms;
public:
    GidToLidMapperFactory(std::shared_ptr<DocumentMetaStore> dms);
    virtual ~GidToLidMapperFactory();
    virtual std::unique_ptr<search::IGidToLidMapper> getMapper() const override;
};

} // namespace proton
