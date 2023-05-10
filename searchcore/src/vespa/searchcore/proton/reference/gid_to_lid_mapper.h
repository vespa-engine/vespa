// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/i_document_meta_store_context.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>

namespace proton {

/*
 * Class for mapping from gid to lid. Instances should be short lived
 * due to read guards preventing resource reuse.
 */
class GidToLidMapper : public search::IGidToLidMapper
{
    search::IDocumentMetaStoreContext::IReadGuard::SP _guard;
public:
    GidToLidMapper(const search::IDocumentMetaStoreContext &dmsContext);
    ~GidToLidMapper() override;
    void foreach(const search::IGidToLidMapperVisitor &visitor) const override;
};

} // namespace proton
