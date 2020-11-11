// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_document_meta_data.h"
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
public:
    static const search::IDocumentMetaStore::DocId FIND_DOC_ID;

private:
    typedef search::IDocumentMetaStore::DocId DocId;
    typedef vespalib::RcuVectorBase<RawDocumentMetaData> MetaDataStore;

    const document::GlobalId &_gid;
    const MetaDataStore      &_metaDataStore;
    const document::GlobalId::BucketOrderCmp _gidCompare;

    const document::GlobalId &getGid(DocId lid) const {
        if (lid != FIND_DOC_ID) {
            return _metaDataStore[lid].getGid();
        }
        return _gid;
    }

public:
    /**
     * Creates a comparator that returns the given gid if
     * FIND_DOC_ID is encountered. Otherwise the metadata store is
     * used to map from lid -> metadata (including gid).
     **/
    LidGidKeyComparator(const document::GlobalId &gid,
                        const MetaDataStore &metaDataStore);

    LidGidKeyComparator(const RawDocumentMetaData &metaData,
                        const MetaDataStore &metaDataStore);

    bool operator()(const DocId &lhs, const DocId &rhs) const {
            return _gidCompare(getGid(lhs), getGid(rhs));
    }

};

}

