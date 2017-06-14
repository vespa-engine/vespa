// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_compaction_handler.h"
#include "document_scan_iterator.h"
#include "ifeedview.h"
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>

using document::BucketId;
using document::Document;
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
LidSpaceCompactionHandler::handleMove(const MoveOperation& op)
{
    _subDb.getFeedView()->handleMove(op);
}

void
LidSpaceCompactionHandler::handleCompactLidSpace(const CompactLidSpaceOperation &op)
{
    assert(_subDb.getSubDbId() == op.getSubDbId());
    _subDb.getFeedView()->handleCompactLidSpace(op);
}

} // namespace proton
