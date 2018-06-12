// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_scan_iterator.h"
#include "ifeedview.h"
#include "lid_space_compaction_handler.h"
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/document/fieldvalue/document.h>

using document::BucketId;
using document::Document;
using search::IDestructorCallback;
using search::LidUsageStats;
using storage::spi::Timestamp;

namespace proton {

LidSpaceCompactionHandler::LidSpaceCompactionHandler(IDocumentSubDB &subDb,
                                                     const vespalib::string &docTypeName)
    : _subDb(subDb),
      _docTypeName(docTypeName)
{
}

LidUsageStats
LidSpaceCompactionHandler::getLidStatus() const
{
    return _subDb.getDocumentMetaStoreContext().get().getLidUsageStats();
}

IDocumentScanIterator::UP
LidSpaceCompactionHandler::getIterator() const
{
    return IDocumentScanIterator::UP(new DocumentScanIterator(
            _subDb.getDocumentMetaStoreContext().get()));
}

MoveOperation::UP
LidSpaceCompactionHandler::createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const
{
    IFeedView::SP feedView = _subDb.getFeedView();
    const ISummaryManager::SP &summaryMan = _subDb.getSummaryManager();
    const uint32_t moveFromLid = document.lid;
    Document::UP doc = summaryMan->getBackingStore().read(moveFromLid, *feedView->getDocumentTypeRepo());
    MoveOperation::UP op(new MoveOperation(document.bucketId, document.timestamp,
                                           Document::SP(doc.release()),
                                           DbDocumentId(_subDb.getSubDbId(), moveFromLid),
                                           _subDb.getSubDbId()));
    op->setTargetLid(moveToLid);
    return op;
}

void
LidSpaceCompactionHandler::handleMove(const MoveOperation& op, IDestructorCallback::SP doneCtx)
{
    _subDb.getFeedView()->handleMove(op, std::move(doneCtx));
}

void
LidSpaceCompactionHandler::handleCompactLidSpace(const CompactLidSpaceOperation &op)
{
    assert(_subDb.getSubDbId() == op.getSubDbId());
    _subDb.getFeedView()->handleCompactLidSpace(op);
}

} // namespace proton
