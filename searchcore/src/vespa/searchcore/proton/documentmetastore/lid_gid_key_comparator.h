// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_document_meta_data.h"
#include "gid_to_lid_map_key.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace proton::documentmetastore {

/**
 * Comparator class used by the lid<->gid btree to get the lids
 * sorted by their gid counterpart.
 **/
class LidGidKeyComparator
{
private:
    typedef search::IDocumentMetaStore::DocId DocId;
    typedef vespalib::RcuVectorBase<RawDocumentMetaData> MetaDataStore;

    const document::GlobalId &_gid;
    const MetaDataStore      &_metaDataStore;
    const document::GlobalId::BucketOrderCmp _gidCompare;

    const document::GlobalId &getGid(const GidToLidMapKey &key) const {
        if (!key.is_find_key()) {
            return _metaDataStore[key.get_lid()].getGid();
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
                        const MetaDataStore &metaDataStore);

    LidGidKeyComparator(const RawDocumentMetaData &metaData,
                        const MetaDataStore &metaDataStore);

    bool operator()(const GidToLidMapKey &lhs, const GidToLidMapKey &rhs) const {
        if (lhs.get_gid_key() != rhs.get_gid_key()) {
            return lhs.get_gid_key() < rhs.get_gid_key();
        }
        return _gidCompare(getGid(lhs), getGid(rhs));
    }

};

}

