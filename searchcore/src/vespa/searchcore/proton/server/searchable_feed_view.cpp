// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchable_feed_view.h"
#include "forcecommitcontext.h"
#include "operationdonecontext.h"
#include "removedonecontext.h"
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/document/fieldvalue/document.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.searchable_feed_view");

using document::BucketId;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using search::index::Schema;
using storage::spi::BucketInfoResult;
using storage::spi::Timestamp;
using vespalib::makeLambdaTask;

namespace proton {

SearchableFeedView::Context::Context(const IIndexWriter::SP &indexWriter)
    : _indexWriter(indexWriter)
{}


SearchableFeedView::Context::~Context() = default;

SearchableFeedView::SearchableFeedView(StoreOnlyFeedView::Context storeOnlyCtx, const PersistentParams &params,
                                       const FastAccessFeedView::Context &fastUpdateCtx, Context ctx)
    : Parent(std::move(storeOnlyCtx), params, fastUpdateCtx),
      _indexWriter(ctx._indexWriter),
      _hasIndexedFields(_schema->getNumIndexFields() > 0)
{ }

SearchableFeedView::~SearchableFeedView() = default;

void
SearchableFeedView::performSync()
{
    // Called by index write thread, delays when sync() method on it completes.
    assert(_writeService.index().isCurrentThread());
    _writeService.indexFieldInverter().sync();
    _writeService.indexFieldWriter().sync();
}

void
SearchableFeedView::sync()
{
    assert(_writeService.master().isCurrentThread());
    Parent::sync();
    _writeService.index().execute(makeLambdaTask([this]() { performSync(); }));
    _writeService.index().sync();
}

void
SearchableFeedView::putIndexedFields(SerialNum serialNum, search::DocumentIdT lid, const DocumentSP &newDoc,
                                     OnOperationDoneType onWriteDone)
{
    if (!hasIndexedFields()) {
        return;
    }
    _writeService.index().execute(
            makeLambdaTask([this, serialNum, lid, newDoc, onWriteDone] {
                performIndexPut(serialNum, lid, newDoc, onWriteDone);
            }));
}

void
SearchableFeedView::performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const Document &doc, OnOperationDoneType onWriteDone)
{
    (void) onWriteDone;
    assert(_writeService.index().isCurrentThread());
    VLOG(getDebugLevel(lid, doc.getId()),
         "database(%s): performIndexPut: serialNum(%" PRIu64 "), docId(%s), lid(%d)",
         _params._docTypeName.toString().c_str(), serialNum, doc.getId().toString().c_str(), lid);

    _indexWriter->put(serialNum, doc, lid);
}

void
SearchableFeedView::performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const DocumentSP &doc, OnOperationDoneType onWriteDone)
{
    performIndexPut(serialNum, lid, *doc, onWriteDone);
}

void
SearchableFeedView::performIndexPut(SerialNum serialNum, search::DocumentIdT lid, FutureDoc futureDoc, OnOperationDoneType onWriteDone)
{
    const auto &doc = futureDoc.get();
    if (doc) {
        performIndexPut(serialNum, lid, *doc, onWriteDone);
    }
}

void
SearchableFeedView::heartBeatIndexedFields(SerialNum serialNum)
{
    _writeService.index().execute(makeLambdaTask([this, serialNum] { performIndexHeartBeat(serialNum); }));
}

void
SearchableFeedView::performIndexHeartBeat(SerialNum serialNum)
{
    _indexWriter->heartBeat(serialNum);
}

void
SearchableFeedView::updateIndexedFields(SerialNum serialNum, search::DocumentIdT lid, FutureDoc futureDoc, OnOperationDoneType onWriteDone)
{
    _writeService.index().execute(
            makeLambdaTask([serialNum, lid, futureDoc = std::move(futureDoc),
                            onWriteDone = std::move(onWriteDone), this]() mutable {
                performIndexPut(serialNum, lid, std::move(futureDoc), std::move(onWriteDone));
            }));
}

void
SearchableFeedView::removeIndexedFields(SerialNum serialNum, search::DocumentIdT lid, OnRemoveDoneType onWriteDone)
{
    if (!hasIndexedFields()) {
        return;
    }
    _writeService.index().execute(
            makeLambdaTask([this, serialNum, lid, onWriteDone]() {
                performIndexRemove(serialNum, lid, onWriteDone);
            }));
}


void
SearchableFeedView::performIndexRemove(SerialNum serialNum, search::DocumentIdT lid, OnRemoveDoneType onWriteDone)
{
    (void) onWriteDone;
    assert(_writeService.index().isCurrentThread());
    VLOG(getDebugLevel(lid, nullptr),
        "database(%s): performIndexRemove: serialNum(%" PRIu64 "), lid(%d)",
         _params._docTypeName.toString().c_str(), serialNum, lid);

    _indexWriter->remove(serialNum, lid);
}

void
SearchableFeedView::performIndexRemove(SerialNum serialNum, const LidVector &lidsToRemove, OnWriteDoneType onWriteDone)
{
    (void) onWriteDone;
    assert(_writeService.index().isCurrentThread());
    for (const auto lid : lidsToRemove) {
        VLOG(getDebugLevel(lid, nullptr),
             "database(%s): performIndexRemove: serialNum(%" PRIu64 "), lid(%d)",
             _params._docTypeName.toString().c_str(), serialNum, lid);

        _indexWriter->remove(serialNum, lid);
    }
}

void
SearchableFeedView::removeIndexedFields(SerialNum serialNum, const LidVector &lidsToRemove,
                                        OnWriteDoneType onWriteDone)
{
    if (!hasIndexedFields())
        return;

    _writeService.index().execute(
            makeLambdaTask([this, serialNum, lidsToRemove, onWriteDone]() {
                performIndexRemove(serialNum, lidsToRemove, onWriteDone);
            }));
}

void
SearchableFeedView::internalDeleteBucket(const DeleteBucketOperation &delOp)
{
    Parent::internalDeleteBucket(delOp);
    _writeService.sync();
}

void
SearchableFeedView::performIndexForceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone)
{
    assert(_writeService.index().isCurrentThread());
    _indexWriter->commit(serialNum, onCommitDone);
}

void
SearchableFeedView::handleCompactLidSpace(const CompactLidSpaceOperation &op)
{
    Parent::handleCompactLidSpace(op);
    _writeService.index().execute(
            makeLambdaTask([this, &op]() {
                _indexWriter->compactLidSpace(op.getSerialNum(), op.getLidLimit());
            }));
    _writeService.index().sync();
}

void
SearchableFeedView::internalForceCommit(const CommitParam & param, OnForceCommitDoneType onCommitDone)
{
    Parent::internalForceCommit(param, onCommitDone);
    _writeService.index().execute(
            makeLambdaTask([this, serialNum=param.lastSerialNum(), onCommitDone]() {
                performIndexForceCommit(serialNum, onCommitDone);
            }));
    _writeService.index().wakeup();
}

} // namespace proton
