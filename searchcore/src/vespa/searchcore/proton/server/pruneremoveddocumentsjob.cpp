// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pruneremoveddocumentsjob.h"
#include "ipruneremoveddocumentshandler.h"
#include "ifrozenbuckethandler.h"
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/vespalib/util/time.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.pruneremoveddocumentsjob");

using document::BucketId;
using storage::spi::Timestamp;

namespace proton {

PruneRemovedDocumentsJob::
PruneRemovedDocumentsJob(const Config &config,
                         const IDocumentMetaStore &metaStore,
                         uint32_t subDbId,
                         const vespalib::string &docTypeName,
                         IPruneRemovedDocumentsHandler &handler,
                         IFrozenBucketHandler &frozenHandler)
    : BlockableMaintenanceJob("prune_removed_documents." + docTypeName,
                              config.getDelay(), config.getInterval()),
      _metaStore(metaStore),
      _subDbId(subDbId),
      _cfgAgeLimit(config.getAge()),
      _docTypeName(docTypeName),
      _handler(handler),
      _frozenHandler(frozenHandler),
      _pruneLids(),
      _nextLid(1u)
{
}


void
PruneRemovedDocumentsJob::flush(DocId lowLid, DocId nextLowLid, const Timestamp ageLimit)
{
    if (_pruneLids.empty())
        return;
    DocId docIdLimit = _metaStore.getCommittedDocIdLimit();
    PruneRemovedDocumentsOperation pruneOp(docIdLimit, _subDbId);
    LidVectorContext::SP lvCtx(pruneOp.getLidsToRemove());
    for (DocId docId : _pruneLids) {
        lvCtx->addLid(docId);
    }
    _pruneLids.clear();
    LOG(debug,
        "PruneRemovedDocumentsJob::flush called,"
        " doctype(%s)"
        " %u lids to prune,"
        " range [%u..%u) limit %u, timestamp %" PRIu64,
        _docTypeName.c_str(),
        static_cast<uint32_t>(pruneOp.getLidsToRemove()->getNumLids()),
        lowLid, nextLowLid, docIdLimit,
        static_cast<uint64_t>(ageLimit));
    _handler.performPruneRemovedDocuments(pruneOp);
}


bool
PruneRemovedDocumentsJob::run()
{
    vespalib::system_time now = vespalib::system_clock::now();
    const Timestamp ageLimit(static_cast<Timestamp::Type>
                             (vespalib::count_us(now.time_since_epoch() - _cfgAgeLimit)));
    DocId lid(_nextLid);
    const DocId olid(lid);
    const DocId docIdLimit(_metaStore.getCommittedDocIdLimit());
    for (uint32_t pass = 0; pass < 10 && lid < docIdLimit; ++pass) {
        const DocId lidLimit = std::min(lid + 10000u, docIdLimit);
        for (; lid < lidLimit; ++lid) {
            if (!_metaStore.validLid(lid))
                continue;
            const RawDocumentMetaData &metaData = _metaStore.getRawMetaData(lid);
            if (metaData.getTimestamp() >= ageLimit)
                continue;
            BucketId bucket(metaData.getBucketId());
            IFrozenBucketHandler::ExclusiveBucketGuard::UP bucketGuard = _frozenHandler.acquireExclusiveBucket(bucket);
            if ( ! bucketGuard ) {
                setBlocked(BlockedReason::FROZEN_BUCKET);
                _nextLid = lid;
                flush(olid, lid, ageLimit);
                return true;
            }
            _pruneLids.push_back(lid);
        }
        if (_pruneLids.size() >= 500)
            break;
    }
    _nextLid = lid;
    flush(olid, lid, ageLimit);
    if (lid >= docIdLimit) {
        _nextLid = 1u;
        return true;
    }
    return false;
}

} // namespace proton
