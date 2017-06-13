// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchable_feed_view.h"
#include "forcecommitcontext.h"
#include "operationdonecontext.h"
#include "removedonecontext.h"
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/metrics/feed_metrics.h>
#include <vespa/searchcore/proton/documentmetastore/ilidreusedelayer.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
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
using proton::matching::MatchContext;
using proton::matching::Matcher;
using search::index::Schema;
using storage::spi::BucketInfoResult;
using storage::spi::Timestamp;
using vespalib::IllegalStateException;
using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::make_string;
using search::makeLambdaTask;

namespace proton {

namespace {

bool shouldTrace(StoreOnlyFeedView::OnOperationDoneType onWriteDone, uint32_t traceLevel) {
    return onWriteDone && onWriteDone->shouldTrace(traceLevel);
}

}

SearchableFeedView::Context::Context(const IIndexWriter::SP &indexWriter,
                                     const std::shared_ptr<IGidToLidChangeHandler> &gidToLidChangeHandler)
    : _indexWriter(indexWriter),
      _gidToLidChangeHandler(gidToLidChangeHandler)
{}


SearchableFeedView::Context::~Context() {}

SearchableFeedView::SearchableFeedView(const StoreOnlyFeedView::Context &storeOnlyCtx,
                                       const PersistentParams &params,
                                       const FastAccessFeedView::Context &fastUpdateCtx,
                                       Context ctx)
    : Parent(storeOnlyCtx, params, fastUpdateCtx),
      _indexWriter(ctx._indexWriter),
      _hasIndexedFields(_schema->getNumIndexFields() > 0),
      _gidToLidChangeHandler(ctx._gidToLidChangeHandler)
{ }

SearchableFeedView::~SearchableFeedView() {}

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
    indexExecute(makeClosure(this, &proton::SearchableFeedView::performSync));
    _writeService.index().sync();
}


void
SearchableFeedView::indexExecute(vespalib::Closure::UP closure)
{
    _writeService.index().execute(makeTask(std::move(closure)));
}


void
SearchableFeedView::putIndexedFields(SerialNum serialNum,
                                     search::DocumentIdT lid,
                                     const Document::SP &newDoc,
                                     bool immediateCommit,
                                     OnOperationDoneType onWriteDone)
{
    if (!hasIndexedFields()) {
        return;
    }
    _writeService.index().execute(
            makeLambdaTask([=]
                           { performIndexPut(serialNum, lid, newDoc,
                                   immediateCommit, onWriteDone); }));
}


void
SearchableFeedView::performIndexPut(SerialNum serialNum,
                                    search::DocumentIdT lid,
                                    const Document::SP &doc,
                                    bool immediateCommit,
                                    OnOperationDoneType onWriteDone)
{
    assert(_writeService.index().isCurrentThread());
    VLOG(getDebugLevel(lid, doc->getId()),
        "database(%s): performIndexPut: serialNum(%" PRIu64 "), "
        "docId(%s), lid(%d)",
        _params._docTypeName.toString().c_str(),
        serialNum,
        doc->getId().toString().c_str(), lid);

    _indexWriter->put(serialNum, *doc, lid);
    if (immediateCommit) {
        _indexWriter->commit(serialNum, onWriteDone);
    }
    if (shouldTrace(onWriteDone, 1)) {
        FeedToken *token = onWriteDone->getToken();
        token->trace(1, "Document indexed = . New Value : " +
                doc->toString(token->shouldTrace(2)));
    }
}


void
SearchableFeedView::heartBeatIndexedFields(SerialNum serialNum)
{
    indexExecute(makeClosure(this,
                             &proton::SearchableFeedView::performIndexHeartBeat,
                             serialNum));
}


void
SearchableFeedView::performIndexHeartBeat(SerialNum serialNum)
{
    _indexWriter->heartBeat(serialNum);
}


SearchableFeedView::UpdateScope
SearchableFeedView::getUpdateScope(const DocumentUpdate &upd)
{
    UpdateScope updateScope;
    const Schema &schema = *_schema;
    for(size_t i(0), m(upd.getUpdates().size());
        !(updateScope._indexedFields && updateScope._nonAttributeFields) &&
            (i < m); i++) {
        const document::FieldUpdate & fu(upd.getUpdates()[i]);
        const vespalib::string &name = fu.getField().getName();
        if (schema.isIndexField(name)) {
            updateScope._indexedFields = true;
        }
        if (!fastPartialUpdateAttribute(name)) {
            updateScope._nonAttributeFields = true;
        }
    }
    if (!upd.getFieldPathUpdates().empty()) {
        updateScope._nonAttributeFields = true;
    }
    return updateScope;
}


void
SearchableFeedView::updateIndexedFields(SerialNum serialNum,
                                        search::DocumentIdT lid,
                                        const Document::SP &newDoc,
                                        bool immediateCommit,
                                        OnOperationDoneType onWriteDone)
{
    if (shouldTrace(onWriteDone, 1)) {
        onWriteDone->getToken()->trace(1, "Then we can update the index.");
    }
    _writeService.index().execute(
            makeLambdaTask([=]()
                           { performIndexPut(serialNum, lid, newDoc,
                                   immediateCommit, onWriteDone); }));
}


void
SearchableFeedView::removeIndexedFields(SerialNum serialNum,
                                        search::DocumentIdT lid,
                                        bool immediateCommit,
                                        OnRemoveDoneType onWriteDone)
{
    if (!hasIndexedFields()) {
        return;
    }
    _writeService.index().execute(
            makeLambdaTask([=]()
                           { performIndexRemove(serialNum, lid,
                                                immediateCommit,
                                                onWriteDone); }));
}


void
SearchableFeedView::performIndexRemove(SerialNum serialNum,
                                       search::DocumentIdT lid,
                                       bool immediateCommit,
                                       OnRemoveDoneType onWriteDone)
{
    assert(_writeService.index().isCurrentThread());
    VLOG(getDebugLevel(lid, NULL),
        "database(%s): performIndexRemove: serialNum(%" PRIu64 "), "
        "lid(%d)",
         _params._docTypeName.toString().c_str(),
         serialNum,
         lid);

    _indexWriter->remove(serialNum, lid);
    if (immediateCommit) {
        _indexWriter->commit(serialNum, onWriteDone);
    }
    FeedToken *token = onWriteDone ? onWriteDone->getToken() : nullptr;
    if (token != nullptr && token->shouldTrace(1)) {
        token->trace(1, make_string("Document with lid %d removed.", lid));
    }
}


void
SearchableFeedView::performIndexRemove(SerialNum serialNum,
                                       const LidVector &lidsToRemove,
                                       bool immediateCommit,
                                       OnWriteDoneType onWriteDone)
{
    assert(_writeService.index().isCurrentThread());
    for (const auto lid : lidsToRemove) {
        VLOG(getDebugLevel(lid, NULL),
             "database(%s): performIndexRemove: serialNum(%" PRIu64 "), "
             "lid(%d)",
             _params._docTypeName.toString().c_str(),
             serialNum,
             lid);

        _indexWriter->remove(serialNum, lid);
    }
    if (immediateCommit) {
        _indexWriter->commit(serialNum, onWriteDone);
    }
}


void
SearchableFeedView::removeIndexedFields(SerialNum serialNum,
                                        const LidVector &lidsToRemove,
                                        bool immediateCommit,
                                        OnWriteDoneType onWriteDone)
{
    if (!hasIndexedFields())
        return;

    _writeService.index().execute(
            makeLambdaTask([=]() { performIndexRemove(serialNum,
                                                      lidsToRemove,
                                                      immediateCommit,
                                                      onWriteDone); }));
}

void
SearchableFeedView::internalDeleteBucket(const DeleteBucketOperation &delOp)
{
    Parent::internalDeleteBucket(delOp);
    _writeService.sync();
}


void
SearchableFeedView::performIndexForceCommit(SerialNum serialNum,
                                            OnForceCommitDoneType onCommitDone)
{
    assert(_writeService.index().isCurrentThread());
    _indexWriter->commit(serialNum, onCommitDone);
}


void
SearchableFeedView::forceCommit(SerialNum serialNum,
                                OnForceCommitDoneType onCommitDone)
{
    Parent::forceCommit(serialNum, onCommitDone);
    _writeService.index().execute(
            makeLambdaTask([=]()
                           { performIndexForceCommit(serialNum,
                                                     onCommitDone); }));
}

void
SearchableFeedView::notifyGidToLidChange(const document::GlobalId &gid, uint32_t lid)
{
    _gidToLidChangeHandler->notifyGidToLidChange(gid, lid);
}

} // namespace proton
