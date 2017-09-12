// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/vespalib/util/generationhandler.h>

namespace proton {

class DocumentMetaStore;

/*
 * Class for mapping from gid to lid. Instances should be short lived
 * due to read guards preventing resource reuse.
 */
class GidToLidMapper : public search::IGidToLidMapper
{
    vespalib::GenerationHandler::Guard _guard;
    const DocumentMetaStore &_dms;
public:
    GidToLidMapper(vespalib::GenerationHandler::Guard &&guard,
                   const DocumentMetaStore &dms);
    virtual ~GidToLidMapper();
    virtual void foreach(const search::IGidToLidMapperVisitor &visitor) const override;
};

} // namespace proton
