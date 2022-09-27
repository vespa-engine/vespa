// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_compaction_handler.h"
#include "document_scan_iterator.h"
#include "ifeedview.h"
#include "maintenancedocumentsubdb.h"
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/documentmetastore/operation_listener.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/idestructorcallback.h>

using document::BucketId;
using document::Document;
using vespalib::IDestructorCallback;
using search::LidUsageStats;
using search::DocumentMetaData;
using search::CommitParam;
using storage::spi::Timestamp;

namespace proton {

LidSpaceCompactionHandler::LidSpaceCompactionHandler(const MaintenanceDocumentSubDB& subDb,
                                                     const vespalib::string& docTypeName)
    : _subDb(subDb),
      _docTypeName(docTypeName)
{
}

LidSpaceCompactionHandler::~LidSpaceCompactionHandler() = default;

vespalib::string
LidSpaceCompactionHandler::getName() const {
    return _docTypeName + "." + _subDb.name();
}

uint32_t
LidSpaceCompactionHandler::getSubDbId() const {
    return _subDb.sub_db_id();
}

void
LidSpaceCompactionHandler::set_operation_listener(documentmetastore::OperationListener::SP op_listener)
{
    return _subDb.meta_store()->set_operation_listener(std::move(op_listener));
}

LidUsageStats
LidSpaceCompactionHandler::getLidStatus() const
{
    return _subDb.meta_store()->getLidUsageStats();
}

IDocumentScanIterator::UP
LidSpaceCompactionHandler::getIterator() const
{
    return std::make_unique<DocumentScanIterator>(*_subDb.meta_store());
}

DocumentMetaData
LidSpaceCompactionHandler::getMetaData(uint32_t lid) const {
    if (_subDb.meta_store()->validLid(lid)) {
        const RawDocumentMetaData &metaData = _subDb.meta_store()->getRawMetaData(lid);
        return DocumentMetaData(lid, metaData.getTimestamp(),
                                metaData.getBucketId(), metaData.getGid());
    }
    return DocumentMetaData();
}

MoveOperation::UP
LidSpaceCompactionHandler::createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const
{
    const uint32_t moveFromLid = document.lid;
    if (_subDb.lidNeedsCommit(moveFromLid)) {
        return MoveOperation::UP();
    }
    auto doc = _subDb.retriever()->getFullDocument(moveFromLid);
    auto op = std::make_unique<MoveOperation>(document.bucketId, storage::spi::Timestamp(document.timestamp),
                                              std::move(doc),
                                              DbDocumentId(_subDb.sub_db_id(), moveFromLid),
                                              _subDb.sub_db_id());
    op->setTargetLid(moveToLid);
    return op;
}

void
LidSpaceCompactionHandler::handleMove(const MoveOperation& op, IDestructorCallback::SP doneCtx)
{
    _subDb.feed_view()->handleMove(op, std::move(doneCtx));
}

void
LidSpaceCompactionHandler::handleCompactLidSpace(const CompactLidSpaceOperation &op, std::shared_ptr<IDestructorCallback> compact_done_context)
{
    assert(_subDb.sub_db_id() == op.getSubDbId());
    _subDb.feed_view()->handleCompactLidSpace(op, compact_done_context);
    _subDb.feed_view()->forceCommit(CommitParam(op.getSerialNum()), std::move(compact_done_context));
}

} // namespace proton
