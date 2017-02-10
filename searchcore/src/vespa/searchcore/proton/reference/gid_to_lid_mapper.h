// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/searchlib/common/idocumentmetastore.h>

namespace proton {

/*
 * Class for mapping from gid to lid. Instances should be short lived
 * due to read guards preventing resource reuse.
 */
class GidToLidMapper : public search::IGidToLidMapper
{
    vespalib::GenerationHandler::Guard _guard;
    const search::IDocumentMetaStore &_dms;
public:
    GidToLidMapper(vespalib::GenerationHandler::Guard &&guard,
                   const search::IDocumentMetaStore &dms);
    virtual ~GidToLidMapper();
    virtual uint32_t mapGidToLid(const document::GlobalId &gid) const override;
};

} // namespace search
