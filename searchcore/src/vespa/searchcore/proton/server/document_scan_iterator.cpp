// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_scan_iterator.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>

using search::DocumentMetaData;

namespace proton {

typedef IDocumentMetaStore::Iterator Iterator;

DocumentScanIterator::DocumentScanIterator(const IDocumentMetaStore &metaStore)
    : _metaStore(metaStore),
      _lastGid(),
      _lastGidValid(false),
      _itrValid(true)
{
}

bool
DocumentScanIterator::valid() const
{
    return _itrValid;
}

DocumentMetaData
DocumentScanIterator::next(uint32_t compactLidLimit,
                           uint32_t maxDocsToScan,
                           bool retry)
{
    Iterator itr = (_lastGidValid ?
            (retry ? _metaStore.lowerBound(_lastGid) : _metaStore.upperBound(_lastGid))
                    : _metaStore.begin());
    uint32_t i = 1; // We have already 'scanned' a document when creating the iterator
    for (; i < maxDocsToScan && itr.valid() && itr.getKey().get_lid() <= compactLidLimit; ++i, ++itr) {}
    if (itr.valid()) {
        uint32_t lid = itr.getKey().get_lid();
        const RawDocumentMetaData &metaData = _metaStore.getRawMetaData(lid);
        _lastGid = metaData.getGid();
        _lastGidValid = true;
        if (lid > compactLidLimit) {
            return DocumentMetaData(lid, metaData.getTimestamp(),
                    metaData.getBucketId(), metaData.getGid());
        }
    } else {
        _itrValid = false;
    }
    return DocumentMetaData();
}

} // namespace proton
