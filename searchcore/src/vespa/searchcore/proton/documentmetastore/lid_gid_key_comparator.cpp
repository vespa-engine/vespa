// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_gid_key_comparator.h"

namespace proton::documentmetastore {

LidGidKeyComparator::LidGidKeyComparator(const document::GlobalId& gid, UnboundMetadataView metadataView)
    : _gid(gid), _metadataView(metadataView), _gidCompare() {
}

LidGidKeyComparator::LidGidKeyComparator(const RawDocumentMetadata& metadata, UnboundMetadataView metadataView)
    : _gid(metadata.getGid()), _metadataView(metadataView), _gidCompare() {
}

} // namespace proton::documentmetastore
