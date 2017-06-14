// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_feed_view.h"
#include "forcecommitcontext.h"
#include "operationdonecontext.h"
#include "removedonecontext.h"
#include "putdonecontext.h"

using document::Document;
using document::DocumentUpdate;
using document::FieldUpdate;
using search::index::Schema;

namespace proton {

FastAccessFeedView::UpdateScope
FastAccessFeedView::getUpdateScope(const DocumentUpdate &upd)
{
    UpdateScope updateScope;
    for (const auto &update : upd.getUpdates()) {
        const vespalib::string &fieldName = update.getField().getName();
        if (!fastPartialUpdateAttribute(fieldName)) {
            updateScope._nonAttributeFields = true;
            break;
        }
    }
    if (!upd.getFieldPathUpdates().empty()) {
        updateScope._nonAttributeFields = true;
    }
    return updateScope;
}

/**
 * NOTE: For both put, update and remove we only need to pass the 'onWriteDone'
 * instance when we are going to commit as part of handling the operation.
 * Otherwise we can drop it and ack the operation right away.
 */
void
FastAccessFeedView::putAttributes(SerialNum serialNum,
                                  search::DocumentIdT lid,
                                  const Document &doc,
                                  bool immediateCommit,
                                  OnPutDoneType onWriteDone)
{
    _attributeWriter->put(serialNum, doc, lid, immediateCommit, onWriteDone);
    if (immediateCommit && onWriteDone) {
        onWriteDone->registerPutLid(lid, &_docIdLimit);
    }
}

void
FastAccessFeedView::updateAttributes(SerialNum serialNum,
                                     search::DocumentIdT lid,
                                     const DocumentUpdate &upd,
                                     bool immediateCommit,
                                     OnOperationDoneType onWriteDone)
{
    _attributeWriter->update(serialNum, upd, lid, immediateCommit, onWriteDone);
}

void
FastAccessFeedView::removeAttributes(SerialNum serialNum,
                                     search::DocumentIdT lid,
                                     bool immediateCommit,
                                     OnRemoveDoneType onWriteDone)
{
    _attributeWriter->remove(serialNum, lid, immediateCommit, onWriteDone);
}

void
FastAccessFeedView::removeAttributes(SerialNum serialNum,
                                     const LidVector &lidsToRemove,
                                     bool immediateCommit,
                                     OnWriteDoneType onWriteDone)
{
    _attributeWriter->remove(lidsToRemove, serialNum, immediateCommit, onWriteDone);
}

void
FastAccessFeedView::heartBeatAttributes(SerialNum serialNum)
{
    _attributeWriter->heartBeat(serialNum);
}

FastAccessFeedView::FastAccessFeedView(const StoreOnlyFeedView::Context &storeOnlyCtx,
                                       const PersistentParams &params,
                                       const Context &ctx)
    : Parent(storeOnlyCtx, params),
      _attributeWriter(ctx._attrWriter),
      _docIdLimit(ctx._docIdLimit)
{}

FastAccessFeedView::~FastAccessFeedView() {}

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
FastAccessFeedView::forceCommit(SerialNum serialNum,
                                OnForceCommitDoneType onCommitDone)
{
    _attributeWriter->commit(serialNum, onCommitDone);
    onCommitDone->registerCommittedDocIdLimit(_metaStore.getCommittedDocIdLimit(), &_docIdLimit);
    Parent::forceCommit(serialNum, onCommitDone);
}


void
FastAccessFeedView::sync()
{
    _writeService.attributeFieldWriter().sync();
}


} // namespace proton
