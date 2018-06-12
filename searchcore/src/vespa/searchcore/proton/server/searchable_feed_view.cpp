// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchable_feed_view.h"
#include "forcecommitcontext.h"
#include "operationdonecontext.h"
#include "removedonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/documentmetastore/ilidreusedelayer.h>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/exceptions.h>

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
using vespalib::IllegalStateException;
using vespalib::make_string;
using vespalib::makeLambdaTask;

namespace proton {

SearchableFeedView::Context::Context(const IIndexWriter::SP &indexWriter)
    : _indexWriter(indexWriter)
{}


SearchableFeedView::Context::~Context() = default;

SearchableFeedView::SearchableFeedView(const StoreOnlyFeedView::Context &storeOnlyCtx, const PersistentParams &params,
                                       const FastAccessFeedView::Context &fastUpdateCtx, Context ctx)
    : Parent(storeOnlyCtx, params, fastUpdateCtx),
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
SearchableFeedView::putIndexedFields(SerialNum serialNum, search::DocumentIdT lid, const Document::SP &newDoc,
                                     bool immediateCommit, OnOperationDoneType onWriteDone)
{
    if (!hasIndexedFields()) {
        return;
    }
    _writeService.index().execute(
            makeLambdaTask([=] {
                performIndexPut(serialNum, lid, newDoc, immediateCommit, onWriteDone);
            }));
}

void
SearchableFeedView::performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const Document &doc,
                                    bool immediateCommit, OnOperationDoneType onWriteDone)
{
    assert(_writeService.index().isCurrentThread());
    VLOG(getDebugLevel(lid, doc.getId()),
         "database(%s): performIndexPut: serialNum(%" PRIu64 "), docId(%s), lid(%d)",
         _params._docTypeName.toString().c_str(), serialNum, doc.getId().toString().c_str(), lid);

    _indexWriter->put(serialNum, doc, lid);
    if (immediateCommit) {
        _indexWriter->commit(serialNum, onWriteDone);
    }
}

void
SearchableFeedView::performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const Document::SP &doc,
                                    bool immediateCommit, OnOperationDoneType onWriteDone)
{
    performIndexPut(serialNum, lid, *doc, immediateCommit, onWriteDone);
}

void
SearchableFeedView::performIndexPut(SerialNum serialNum, search::DocumentIdT lid, FutureDoc futureDoc,
                                    bool immediateCommit, OnOperationDoneType onWriteDone)
{
    const auto &doc = futureDoc.get();
    if (doc) {
        performIndexPut(serialNum, lid, *doc, immediateCommit, onWriteDone);
    }
}

void
SearchableFeedView::heartBeatIndexedFields(SerialNum serialNum)
{
    _writeService.index().execute(makeLambdaTask([=] { performIndexHeartBeat(serialNum); }));
}

void
SearchableFeedView::performIndexHeartBeat(SerialNum serialNum)
{
    _indexWriter->heartBeat(serialNum);
}

void
SearchableFeedView::updateIndexedFields(SerialNum serialNum, search::DocumentIdT lid, FutureDoc futureDoc,
                                        bool immediateCommit, OnOperationDoneType onWriteDone)
{
    _writeService.index().execute(
            makeLambdaTask([serialNum, lid, futureDoc = std::move(futureDoc),
                            immediateCommit, onWriteDone = std::move(onWriteDone), this]() mutable {
                performIndexPut(serialNum, lid, std::move(futureDoc), immediateCommit, std::move(onWriteDone));
            }));
}

void
SearchableFeedView::removeIndexedFields(SerialNum serialNum, search::DocumentIdT lid,
                                        bool immediateCommit, OnRemoveDoneType onWriteDone)
{
    if (!hasIndexedFields()) {
        return;
    }
    _writeService.index().execute(
            makeLambdaTask([=]() {
                performIndexRemove(serialNum, lid, immediateCommit, onWriteDone);
            }));
}


void
SearchableFeedView::performIndexRemove(SerialNum serialNum, search::DocumentIdT lid,
                                       bool immediateCommit, OnRemoveDoneType onWriteDone)
{
    assert(_writeService.index().isCurrentThread());
    VLOG(getDebugLevel(lid, nullptr),
        "database(%s): performIndexRemove: serialNum(%" PRIu64 "), lid(%d)",
         _params._docTypeName.toString().c_str(), serialNum, lid);

    _indexWriter->remove(serialNum, lid);
    if (immediateCommit) {
        _indexWriter->commit(serialNum, onWriteDone);
    }
}

void
SearchableFeedView::performIndexRemove(SerialNum serialNum, const LidVector &lidsToRemove,
                                       bool immediateCommit, OnWriteDoneType onWriteDone)
{
    assert(_writeService.index().isCurrentThread());
    for (const auto lid : lidsToRemove) {
        VLOG(getDebugLevel(lid, nullptr),
             "database(%s): performIndexRemove: serialNum(%" PRIu64 "), lid(%d)",
             _params._docTypeName.toString().c_str(), serialNum, lid);

        _indexWriter->remove(serialNum, lid);
    }
    if (immediateCommit) {
        _indexWriter->commit(serialNum, onWriteDone);
    }
}

void
SearchableFeedView::removeIndexedFields(SerialNum serialNum, const LidVector &lidsToRemove,
                                        bool immediateCommit, OnWriteDoneType onWriteDone)
{
    if (!hasIndexedFields())
        return;

    _writeService.index().execute(
            makeLambdaTask([=]() {
                performIndexRemove(serialNum, lidsToRemove, immediateCommit, onWriteDone);
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
SearchableFeedView::forceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone)
{
    Parent::forceCommit(serialNum, onCommitDone);
    _writeService.index().execute(makeLambdaTask([=]() { performIndexForceCommit(serialNum, onCommitDone); }));
}

} // namespace proton
