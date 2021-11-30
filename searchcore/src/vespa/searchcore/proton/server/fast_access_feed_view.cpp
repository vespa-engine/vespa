// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_feed_view.h"
#include "forcecommitcontext.h"
#include "operationdonecontext.h"
#include "removedonecontext.h"
#include "putdonecontext.h"
#include <vespa/searchcore/proton/feedoperation/operations.h>

using document::Document;
using document::DocumentUpdate;
using search::index::Schema;

namespace proton {

/**
 * NOTE: For both put, update and remove we only need to pass the 'onWriteDone'
 * instance when we are going to commit as part of handling the operation.
 * Otherwise we can drop it and ack the operation right away.
 */
void
FastAccessFeedView::putAttributes(SerialNum serialNum, search::DocumentIdT lid, const Document &doc, OnPutDoneType onWriteDone)
{
    _attributeWriter->put(serialNum, doc, lid, onWriteDone);
}

void
FastAccessFeedView::updateAttributes(SerialNum serialNum, search::DocumentIdT lid, const DocumentUpdate &upd,
                                     OnOperationDoneType onWriteDone, IFieldUpdateCallback & onUpdate)
{
    _attributeWriter->update(serialNum, upd, lid, onWriteDone, onUpdate);
}

void
FastAccessFeedView::updateAttributes(SerialNum serialNum, Lid lid, FutureDoc futureDoc, OnOperationDoneType onWriteDone)
{
    if (_attributeWriter->hasStructFieldAttribute()) {
        const std::unique_ptr<const Document> & doc = futureDoc.get();
        if (doc) {
            _attributeWriter->update(serialNum, *doc, lid, onWriteDone);
        }
    }
}

void
FastAccessFeedView::removeAttributes(SerialNum serialNum, search::DocumentIdT lid, OnRemoveDoneType onWriteDone)
{
    _attributeWriter->remove(serialNum, lid, onWriteDone);
}

void
FastAccessFeedView::removeAttributes(SerialNum serialNum, const LidVector &lidsToRemove, OnWriteDoneType onWriteDone)
{
    _attributeWriter->remove(lidsToRemove, serialNum, onWriteDone);
}

void
FastAccessFeedView::heartBeatAttributes(SerialNum serialNum, DoneCallback onDone)
{
    _attributeWriter->heartBeat(serialNum, onDone);
}

FastAccessFeedView::FastAccessFeedView(StoreOnlyFeedView::Context storeOnlyCtx, const PersistentParams &params, const Context &ctx)
    : Parent(std::move(storeOnlyCtx), params),
      _attributeWriter(ctx._attrWriter),
      _docIdLimit(ctx._docIdLimit)
{}

FastAccessFeedView::~FastAccessFeedView() = default;

void
FastAccessFeedView::handleCompactLidSpace(const CompactLidSpaceOperation &op, DoneCallback onDone)
{
    // Drain pending PutDoneContext and ForceCommitContext objects
    forceCommitAndWait(search::CommitParam(op.getSerialNum()));
    _docIdLimit.set(op.getLidLimit());
    getAttributeWriter()->compactLidSpace(op.getLidLimit(), op.getSerialNum());
    Parent::handleCompactLidSpace(op, onDone);
}

void
FastAccessFeedView::internalForceCommit(const CommitParam & param, OnForceCommitDoneType onCommitDone)
{
    _attributeWriter->forceCommit(param, onCommitDone);
    onCommitDone->registerCommittedDocIdLimit(_metaStore.getCommittedDocIdLimit(), &_docIdLimit);
    Parent::internalForceCommit(param, onCommitDone);
}

} // namespace proton
