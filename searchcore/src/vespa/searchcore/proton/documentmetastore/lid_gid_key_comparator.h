// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_document_metadata.h"
#include "gid_to_lid_map_key.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/idocumentmetastore.h>

namespace proton::documentmetastore {

/**
 * Comparator class used by the lid<->gid btree to get the lids
 * sorted by their gid counterpart.
 **/
class LidGidKeyComparator
{
private:
    using DocId = search::IDocumentMetaStore::DocId;
    using UnboundMetadataView = const RawDocumentMetadata *;

    const document::GlobalId &_gid;
    UnboundMetadataView       _metaDataView;
    const document::GlobalId::BucketOrderCmp _gidCompare;

    const document::GlobalId &getGid(const GidToLidMapKey &key) const {
        if (!key.is_find_key()) {
            return _metaDataView[key.get_lid()].getGid();
        }
        return _gid;
    }

public:
    /**
     * Creates a comparator that returns the given gid if
     * key is a find key. Otherwise the metadata store is
     * used to map from lid -> metadata (including gid).
     **/
    LidGidKeyComparator(const document::GlobalId &gid,
                        UnboundMetadataView metaDataView);

    LidGidKeyComparator(const RawDocumentMetadata &metaData,
                        UnboundMetadataView metaDataView);

    bool operator()(const GidToLidMapKey &lhs, const GidToLidMapKey &rhs) const {
        if (lhs.get_gid_key() != rhs.get_gid_key()) {
            return lhs.get_gid_key() < rhs.get_gid_key();
        }
        return _gidCompare(getGid(lhs), getGid(rhs));
    }

};

}

