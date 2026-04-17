// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_scan_iterator.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>

using search::DocumentMetadata;

namespace proton {

using Iterator = IDocumentMetaStore::Iterator;

DocumentScanIterator::DocumentScanIterator(const IDocumentMetaStore &metaStore)
    : _metaStore(metaStore),
      _lastLid(_metaStore.getLidUsageStats().getHighestUsedLid() + 1),
      _itrValid(true)
{
}

bool
DocumentScanIterator::valid() const
{
    return _itrValid;
}

DocumentMetadata
DocumentScanIterator::next(uint32_t compactLidLimit)
{
    for (--_lastLid; _lastLid > compactLidLimit; --_lastLid) {
        if (_metaStore.validLid(_lastLid)) {
            const RawDocumentMetadata &metadata = _metaStore.getRawMetadata(_lastLid);
            return DocumentMetadata(_lastLid, metadata.getTimestamp(),
                                    metadata.getBucketId(), metadata.getGid());
        }
    }
    _itrValid = (_lastLid > compactLidLimit) ;
    return DocumentMetadata();
}

} // namespace proton
