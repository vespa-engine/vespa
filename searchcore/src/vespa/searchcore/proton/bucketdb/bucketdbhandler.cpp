// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdbhandler.h"
#include "splitbucketsession.h"
#include "joinbucketssession.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <algorithm>

namespace proton::bucketdb {

BucketDBHandler::BucketDBHandler(BucketDBOwner &bucketDB)
    : _bucketDB(bucketDB),
      _dmsv(),
      _bucketCreateNotifier()
{
}

BucketDBHandler::~BucketDBHandler() = default;

void
BucketDBHandler::addDocumentMetaStore(IDocumentMetaStore *dms, search::SerialNum flushedSerialNum)
{
    _dmsv.push_back(MetaStoreDesc(dms, flushedSerialNum));
}

void
BucketDBHandler::handleSplit(search::SerialNum serialNum, const BucketId &source,
                             const BucketId &target1, const BucketId &target2)
{
    // Called by writer thread
    assert(source.valid());
    assert(target1.valid() || target2.valid());
    if (target1.valid()) {
        assert(source.getUsedBits() < target1.getUsedBits());
        assert(source.contains(target1));
    }
    if (target2.valid()) {
        assert(source.getUsedBits() < target2.getUsedBits());
        assert(source.contains(target2));
    }
    if (target1.valid() && target2.valid()) {
        assert(target1 != target2);
        assert(!target1.contains(target2));
        assert(!target2.contains(target1));
    }
    SplitBucketSession session(_bucketDB, _bucketCreateNotifier, source, target1, target2);
    session.setup();
    for (auto &desc : _dmsv) {
        IDocumentMetaStore *dms = desc._dms;
        if (serialNum > desc._flushedSerialNum) {
            BucketDeltaPair deltas = dms->handleSplit(session);
            session.applyDeltas(deltas);
            dms->commit(search::CommitParam(serialNum));
        }
    }
    session.finish();
}


void
BucketDBHandler::handleJoin(search::SerialNum serialNum, const BucketId &source1,
                            const BucketId &source2, const BucketId &target)
{
    // Called by writer thread
    JoinBucketsSession session(_bucketDB, _bucketCreateNotifier, source1, source2, target);
    session.setup();
    for (auto &desc : _dmsv) {
        IDocumentMetaStore *dms = desc._dms;
        if (serialNum > desc._flushedSerialNum) {
            BucketDeltaPair deltas = dms->handleJoin(session);
            session.applyDeltas(deltas);
            dms->commit(search::CommitParam(serialNum));
        }
    }
    session.finish();
}

void
BucketDBHandler::handleCreateBucket(const BucketId &bucketId)
{
    _bucketDB.takeGuard()->createBucket(bucketId);
}

void
BucketDBHandler::handleDeleteBucket(const BucketId &bucketId)
{
    _bucketDB.takeGuard()->deleteEmptyBucket(bucketId);
}

}

