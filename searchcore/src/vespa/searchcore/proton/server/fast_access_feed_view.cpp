// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_feed_view.h"
#include "forcecommitcontext.h"
#include "operationdonecontext.h"
#include "removedonecontext.h"
#include "putdonecontext.h"
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>

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
FastAccessFeedView::putAttributes(SerialNum serialNum, search::DocumentIdT lid, const Document &doc,
                                  bool immediateCommit, OnPutDoneType onWriteDone)
{
    _attributeWriter->put(serialNum, doc, lid, immediateCommit, onWriteDone);
    if (immediateCommit && onWriteDone) {
        onWriteDone->registerPutLid(&_docIdLimit);
    }
}

void
FastAccessFeedView::updateAttributes(SerialNum serialNum, search::DocumentIdT lid, const DocumentUpdate &upd,
                                     bool immediateCommit, OnOperationDoneType onWriteDone, IFieldUpdateCallback & onUpdate)
{
    _attributeWriter->update(serialNum, upd, lid, immediateCommit, onWriteDone, onUpdate);
}

void
FastAccessFeedView::updateAttributes(SerialNum serialNum, Lid lid, FutureDoc doc,
                                     bool immediateCommit, OnOperationDoneType onWriteDone)
{
    if (_attributeWriter->hasStructFieldAttribute()) {
        _attributeWriter->update(serialNum, *doc.get(), lid, immediateCommit, onWriteDone);
    }
}

void
FastAccessFeedView::removeAttributes(SerialNum serialNum, search::DocumentIdT lid,
                                     bool immediateCommit, OnRemoveDoneType onWriteDone)
{
    _attributeWriter->remove(serialNum, lid, immediateCommit, onWriteDone);
}

void
FastAccessFeedView::removeAttributes(SerialNum serialNum, const LidVector &lidsToRemove,
                                     bool immediateCommit, OnWriteDoneType onWriteDone)
{
    _attributeWriter->remove(lidsToRemove, serialNum, immediateCommit, onWriteDone);
}

void
FastAccessFeedView::heartBeatAttributes(SerialNum serialNum)
{
    _attributeWriter->heartBeat(serialNum);
}

FastAccessFeedView::FastAccessFeedView(const StoreOnlyFeedView::Context &storeOnlyCtx,
                                       const PersistentParams &params, const Context &ctx)
    : Parent(storeOnlyCtx, params),
      _attributeWriter(ctx._attrWriter),
      _docIdLimit(ctx._docIdLimit)
{}

FastAccessFeedView::~FastAccessFeedView() = default;

void
FastAccessFeedView::handleCompactLidSpace(const CompactLidSpaceOperation &op)
{
    // Drain pending PutDoneContext and ForceCommitContext objects
    _writeService.sync();
    _docIdLimit.set(op.getLidLimit());
    getAttributeWriter()->compactLidSpace(op.getLidLimit(), op.getSerialNum());
    Parent::handleCompactLidSpace(op);
}

void
FastAccessFeedView::forceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone)
{
    _attributeWriter->forceCommit(serialNum, onCommitDone);
    onCommitDone->registerCommittedDocIdLimit(_metaStore.getCommittedDocIdLimit(), &_docIdLimit);
    Parent::forceCommit(serialNum, onCommitDone);
}


void
FastAccessFeedView::sync()
{
    Parent::sync();
    _writeService.attributeFieldWriter().sync();
}

} // namespace proton
