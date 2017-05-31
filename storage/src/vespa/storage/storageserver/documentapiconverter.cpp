// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "documentapiconverter.h"
#include <vespa/documentapi/documentapi.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/searchresult.h>
#include <vespa/storageapi/message/queryresult.h>
#include <vespa/storageapi/message/documentsummary.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/vdslib/container/documentlist.h>
#include <vespa/document/bucket/bucketidfactory.h>

#include <vespa/log/log.h>
LOG_SETUP(".documentapiconverter");

namespace storage {

std::unique_ptr<api::StorageCommand>
DocumentApiConverter::toStorageAPI(documentapi::DocumentMessage& fromMsg,
                                   const document::DocumentTypeRepo::SP &repo)
{
    api::StorageCommand::UP toMsg;

    using documentapi::DocumentProtocol;
    switch (fromMsg.getType()) {
    case DocumentProtocol::MESSAGE_PUTDOCUMENT:
    {
        documentapi::PutDocumentMessage& from(
                static_cast<documentapi::PutDocumentMessage&>(fromMsg));
        api::PutCommand::UP to(new api::PutCommand(
                document::BucketId(0), from.getDocument(),
                from.getTimestamp()));
        to->setCondition(from.getCondition());
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_UPDATEDOCUMENT:
    {
        documentapi::UpdateDocumentMessage& from(
                static_cast<documentapi::UpdateDocumentMessage&>(fromMsg));
        api::UpdateCommand::UP to(new api::UpdateCommand(
                document::BucketId(0), from.getDocumentUpdate(),
                from.getNewTimestamp()));
        to->setOldTimestamp(from.getOldTimestamp());
        to->setCondition(from.getCondition());
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_REMOVEDOCUMENT:
    {
        documentapi::RemoveDocumentMessage& from(
                static_cast<documentapi::RemoveDocumentMessage&>(fromMsg));
        api::RemoveCommand::UP to(new api::RemoveCommand(
                document::BucketId(0), from.getDocumentId(), 0));
        to->setCondition(from.getCondition());
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_GETDOCUMENT:
    {
        documentapi::GetDocumentMessage& from(
                static_cast<documentapi::GetDocumentMessage&>(fromMsg));
        api::GetCommand::UP to(new api::GetCommand(
                document::BucketId(0), from.getDocumentId(),
                from.getFieldSet()));
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_CREATEVISITOR:
    {
        documentapi::CreateVisitorMessage& from(
                static_cast<documentapi::CreateVisitorMessage&>(fromMsg));
        api::CreateVisitorCommand::UP to(new api::CreateVisitorCommand(
                    from.getLibraryName(),
                    from.getInstanceId(),
                    from.getDocumentSelection()));

        to->setControlDestination(from.getControlDestination());
        to->setDataDestination(from.getDataDestination());
        to->setMaximumPendingReplyCount(from.getMaximumPendingReplyCount());
        to->setParameters(from.getParameters());
        to->setFromTime(from.getFromTimestamp());
        to->setToTime(from.getToTimestamp());
        to->setVisitRemoves(from.visitRemoves());
        to->setFieldSet(from.getFieldSet());
        to->setVisitInconsistentBuckets(from.visitInconsistentBuckets());
        to->getBuckets() = from.getBuckets();
        to->setVisitorDispatcherVersion(from.getVisitorDispatcherVersion());
        to->setVisitorOrdering(from.getVisitorOrdering());
        to->setMaxBucketsPerVisitor(from.getMaxBucketsPerVisitor());
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_DESTROYVISITOR:
    {
        documentapi::DestroyVisitorMessage& from(
                static_cast<documentapi::DestroyVisitorMessage&>(fromMsg));
        api::DestroyVisitorCommand::UP to(new api::DestroyVisitorCommand(
                    from.getInstanceId()));
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_MULTIOPERATION:
    {
        documentapi::MultiOperationMessage& from(
                static_cast<documentapi::MultiOperationMessage&>(fromMsg));
        api::MultiOperationCommand::UP to(new api::MultiOperationCommand(repo,
                        from.getBucketId(), from.getBuffer(),
                        from.keepTimeStamps()));
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_BATCHDOCUMENTUPDATE:
    {
        documentapi::BatchDocumentUpdateMessage& from(
                static_cast<documentapi::BatchDocumentUpdateMessage&>(fromMsg));
        api::BatchDocumentUpdateCommand::UP to(
                new api::BatchDocumentUpdateCommand(from.getUpdates()));
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_STATBUCKET:
    {
        documentapi::StatBucketMessage& from(
                static_cast<documentapi::StatBucketMessage&>(fromMsg));
        api::StatBucketCommand::UP to(new api::StatBucketCommand(
                from.getBucketId(), from.getDocumentSelection()));
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_GETBUCKETLIST:
    {
        documentapi::GetBucketListMessage& from(
                static_cast<documentapi::GetBucketListMessage&>(fromMsg));
        api::GetBucketListCommand::UP to(new api::GetBucketListCommand(
                    from.getBucketId()));
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_VISITORINFO:
    {
        documentapi::VisitorInfoMessage& from(
                static_cast<documentapi::VisitorInfoMessage&>(fromMsg));
        api::VisitorInfoCommand::UP to(new api::VisitorInfoCommand);
        for (uint32_t i = 0; i < from.getFinishedBuckets().size(); ++i) {
            to->setBucketCompleted(from.getFinishedBuckets()[i], 0);
        }
        if (!from.getErrorMessage().empty()) {
            to->setErrorCode(api::ReturnCode(
                    api::ReturnCode::INTERNAL_FAILURE, from.getErrorMessage()));
        }
        toMsg.reset(to.release());
        break;
    }
    case DocumentProtocol::MESSAGE_REMOVELOCATION:
    {
        documentapi::RemoveLocationMessage& from(
                static_cast<documentapi::RemoveLocationMessage&>(fromMsg));
        api::RemoveLocationCommand::UP to(new api::RemoveLocationCommand(
                from.getDocumentSelection(), document::BucketId(0)));
        toMsg.reset(to.release());
        break;
    }
    default:
        break;
    }

    if (toMsg.get() != 0) {
        int64_t timeout = fromMsg.getTimeRemaining();
        if (timeout > INT_MAX) {
            timeout = INT_MAX;
        }
        toMsg->setTimeout(timeout);
        toMsg->setPriority(
                _priConverter.toStoragePriority(fromMsg.getPriority()));
        toMsg->setLoadType(fromMsg.getLoadType());

        LOG(spam, "Converted command %s, loadtype %d, mapped priority %d to %d",
            toMsg->toString().c_str(), toMsg->getLoadType().getId(),
            fromMsg.getPriority(), toMsg->getPriority());
    }
    return std::move(toMsg);
}

std::unique_ptr<api::StorageReply>
DocumentApiConverter::toStorageAPI(documentapi::DocumentReply& fromReply,
                                   api::StorageCommand& fromCommand)
{
    if (LOG_WOULD_LOG(spam)) {
        LOG(spam, "Trace for reply:\n%s",
            fromReply.getTrace().toString().c_str());
    }
    std::unique_ptr<api::StorageReply> toMsg;

    switch (fromReply.getType()) {
    case documentapi::DocumentProtocol::REPLY_CREATEVISITOR:
    {
        documentapi::CreateVisitorReply& fromRep(
                static_cast<documentapi::CreateVisitorReply&>(fromReply));
        const api::CreateVisitorCommand& fromCmd(
                static_cast<const api::CreateVisitorCommand&>(fromCommand));

        api::CreateVisitorReply::UP to(new api::CreateVisitorReply(fromCmd));
        to->setVisitorStatistics(fromRep.getVisitorStatistics());
        toMsg.reset(to.release());
        break;
    }
    case documentapi::DocumentProtocol::REPLY_STATBUCKET:
    {
        documentapi::StatBucketReply& fromRep(
                static_cast<documentapi::StatBucketReply&>(fromReply));
        const api::StatBucketCommand& fromCmd(
                static_cast<const api::StatBucketCommand&>(fromCommand));

        api::StatBucketReply::UP to(
                new api::StatBucketReply(fromCmd, fromRep.getResults()));
        toMsg.reset(to.release());
        break;
    }
    default:
        toMsg = fromCommand.makeReply();
        break;
    }

    if (toMsg.get()) {
        if (fromReply.hasErrors()) {
            toMsg->setResult(api::ReturnCode(
                    (api::ReturnCode::Result) fromReply.getError(0).getCode(),
                    fromReply.getError(0).getMessage()));
            toMsg->setPriority(
                    _priConverter.toStoragePriority(fromReply.getPriority()));
        }
    }
    return std::move(toMsg);
}

std::unique_ptr<mbus::Message>
DocumentApiConverter::toDocumentAPI(api::StorageCommand& fromMsg,
                                    const document::DocumentTypeRepo::SP &repo)
{
    std::unique_ptr<mbus::Message> toMsg;
    switch (fromMsg.getType().getId()) {
    case api::MessageType::PUT_ID:
    {
        api::PutCommand& from(static_cast<api::PutCommand&>(fromMsg));
        documentapi::PutDocumentMessage::UP to(
                new documentapi::PutDocumentMessage(from.getDocument()));
        to->setTimestamp(from.getTimestamp());
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::UPDATE_ID:
    {
        api::UpdateCommand& from(static_cast<api::UpdateCommand&>(fromMsg));
        documentapi::UpdateDocumentMessage::UP to(
                new documentapi::UpdateDocumentMessage(from.getUpdate()));
        to->setOldTimestamp(from.getOldTimestamp());
        to->setNewTimestamp(from.getTimestamp());
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::REMOVE_ID:
    {
        api::RemoveCommand& from(static_cast<api::RemoveCommand&>(fromMsg));
        documentapi::RemoveDocumentMessage::UP to(
                new documentapi::RemoveDocumentMessage(from.getDocumentId()));
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::VISITOR_INFO_ID:
    {
        api::VisitorInfoCommand& from(
                static_cast<api::VisitorInfoCommand&>(fromMsg));
        documentapi::VisitorInfoMessage::UP to(
                new documentapi::VisitorInfoMessage);

        for (uint32_t i = 0; i < from.getCompletedBucketsList().size(); ++i) {
            to->getFinishedBuckets().push_back(
                    from.getCompletedBucketsList()[i].bucketId);
        }
        to->setErrorMessage(from.getErrorCode().getMessage());
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::DOCBLOCK_ID:
    {
        api::DocBlockCommand& from(static_cast<api::DocBlockCommand&>(fromMsg));
        documentapi::MultiOperationMessage::UP to(
                new documentapi::MultiOperationMessage(
                    from.getBucketId(),
                    from.getDocumentBlock(),
                    from.keepTimeStamps()));
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::SEARCHRESULT_ID:
    {
        api::SearchResultCommand& from(
                static_cast<api::SearchResultCommand&>(fromMsg));
        documentapi::SearchResultMessage::UP to(
                new documentapi::SearchResultMessage(from));
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::QUERYRESULT_ID:
    {
        api::QueryResultCommand& from(
                static_cast<api::QueryResultCommand&>(fromMsg));
        documentapi::QueryResultMessage::UP to(
                new documentapi::QueryResultMessage(
                    from.getSearchResult(), from.getDocumentSummary()));
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::DOCUMENTSUMMARY_ID:
    {
        api::DocumentSummaryCommand& from(
                static_cast<api::DocumentSummaryCommand&>(fromMsg));
        documentapi::DocumentSummaryMessage::UP to(
                new documentapi::DocumentSummaryMessage(from));
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::MULTIOPERATION_ID:
    {
        api::MultiOperationCommand& from(
                static_cast<api::MultiOperationCommand&>(fromMsg));
        documentapi::MultiOperationMessage::UP to(
                new documentapi::MultiOperationMessage(repo,
                        from.getBucketId(),
                        from.getBuffer(),
                        from.keepTimeStamps()));
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::MAPVISITOR_ID:
    {
        api::MapVisitorCommand& from(
                static_cast<api::MapVisitorCommand&>(fromMsg));
        documentapi::MapVisitorMessage::UP to(
                new documentapi::MapVisitorMessage);
        to->getData() = from.getData();
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::DOCUMENTLIST_ID:
    {
        api::DocumentListCommand& from(
                static_cast<api::DocumentListCommand&>(fromMsg));
        documentapi::DocumentListMessage::UP to(
                new documentapi::DocumentListMessage(from.getBucketId()));

        for (uint32_t i = 0; i < from.getDocuments().size(); i++) {
            to->getDocuments().push_back(
                    documentapi::DocumentListMessage::Entry(
                        from.getDocuments()[i]._lastModified,
                        from.getDocuments()[i]._doc,
                        from.getDocuments()[i]._removeEntry));
        }
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::EMPTYBUCKETS_ID:
    {
        api::EmptyBucketsCommand& from(
                static_cast<api::EmptyBucketsCommand&>(fromMsg));
        std::unique_ptr<documentapi::EmptyBucketsMessage> to(
                new documentapi::EmptyBucketsMessage(from.getBuckets()));
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::VISITOR_CREATE_ID:
    {
        api::CreateVisitorCommand& from(
                static_cast<api::CreateVisitorCommand&>(fromMsg));
        documentapi::CreateVisitorMessage::UP to(
                new documentapi::CreateVisitorMessage(
                    from.getLibraryName(),
                    from.getInstanceId(),
                    from.getControlDestination(),
                    from.getDataDestination()));
        to->setDocumentSelection(from.getDocumentSelection());
        to->setMaximumPendingReplyCount(from.getMaximumPendingReplyCount());
        to->setParameters(from.getParameters());
        to->setFromTimestamp(from.getFromTime());
        to->setToTimestamp(from.getToTime());
        to->setVisitRemoves(from.visitRemoves());
        to->setFieldSet(from.getFieldSet());
        to->setVisitInconsistentBuckets(from.visitInconsistentBuckets());
        to->getBuckets() = from.getBuckets();
        to->setVisitorOrdering(from.getVisitorOrdering());
        to->setMaxBucketsPerVisitor(from.getMaxBucketsPerVisitor());
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::VISITOR_DESTROY_ID:
    {
        api::DestroyVisitorCommand& from(
                static_cast<api::DestroyVisitorCommand&>(fromMsg));
        documentapi::DestroyVisitorMessage::UP to(
                new documentapi::DestroyVisitorMessage);
        to->setInstanceId(from.getInstanceId());
        toMsg.reset(to.release());
        break;
    }
    case api::MessageType::STATBUCKET_ID:
    {
        api::StatBucketCommand& from(
                static_cast<api::StatBucketCommand&>(fromMsg));
        documentapi::StatBucketMessage::UP to(
                new documentapi::StatBucketMessage(
                    from.getBucketId(), from.getDocumentSelection()));
        toMsg.reset(to.release());
        break;
    }
    default:
        break;
    }

    if (toMsg.get()) {
        toMsg->setTimeRemaining(fromMsg.getTimeout());
        toMsg->setContext(mbus::Context(fromMsg.getMsgId()));
        if (LOG_WOULD_LOG(spam)) {
            toMsg->getTrace().setLevel(9);
        }
    }
    return std::move(toMsg);
}

void
DocumentApiConverter::transferReplyState(api::StorageReply& fromMsg,
                                         mbus::Reply& toMsg)
{
        // First map error codes.
    if (fromMsg.getResult().failed()) {
        mbus::Error error(mbus::Error(fromMsg.getResult().getResult(),
                                      fromMsg.getResult().toString()));
        toMsg.addError(error);
        LOG(debug, "Converted storageapi error code %d to %s",
            fromMsg.getResult().getResult(), error.toString().c_str());
    }
        // Then map specifics for specific types of messages needing it
    using documentapi::DocumentProtocol;
    if (toMsg.getType() == DocumentProtocol::REPLY_GETDOCUMENT) {
        api::GetReply& from(static_cast<api::GetReply&>(fromMsg));
        documentapi::GetDocumentReply& to(
                static_cast<documentapi::GetDocumentReply&>(toMsg));
        if (from.getDocument().get() != 0) {
            to.setDocument(from.getDocument());
            to.setLastModified(from.getLastModifiedTimestamp());
        }
    } else if (toMsg.getType() == DocumentProtocol::REPLY_REMOVEDOCUMENT) {
        api::RemoveReply& from(static_cast<api::RemoveReply&>(fromMsg));
        documentapi::RemoveDocumentReply& to(
                static_cast<documentapi::RemoveDocumentReply&>(toMsg));
        to.setWasFound(from.wasFound());
        to.setHighestModificationTimestamp(from.getTimestamp());
    } else if (toMsg.getType() == DocumentProtocol::REPLY_PUTDOCUMENT) {
        api::PutReply& from(static_cast<api::PutReply&>(fromMsg));
        documentapi::WriteDocumentReply& to(
                static_cast<documentapi::WriteDocumentReply&>(toMsg));
        to.setHighestModificationTimestamp(from.getTimestamp());
    } else if (toMsg.getType() == DocumentProtocol::REPLY_MULTIOPERATION) {
        api::MultiOperationReply& from(
                static_cast<api::MultiOperationReply&>(fromMsg));
        documentapi::WriteDocumentReply& to(
                static_cast<documentapi::WriteDocumentReply&>(toMsg));
        to.setHighestModificationTimestamp(
                from.getHighestModificationTimestamp());
    } else if (toMsg.getType() == DocumentProtocol::REPLY_UPDATEDOCUMENT) {
        api::UpdateReply& from(static_cast<api::UpdateReply&>(fromMsg));
        documentapi::UpdateDocumentReply& to(
                static_cast<documentapi::UpdateDocumentReply&>(toMsg));
        to.setWasFound(from.wasFound());
        to.setHighestModificationTimestamp(from.getTimestamp());
    } else if (toMsg.getType() == DocumentProtocol::REPLY_STATBUCKET) {
        api::StatBucketReply& from(static_cast<api::StatBucketReply&>(fromMsg));
        documentapi::StatBucketReply& to(
                static_cast<documentapi::StatBucketReply&>(toMsg));
        to.setResults(from.getResults());
    } else if (toMsg.getType() == DocumentProtocol::REPLY_GETBUCKETLIST) {
        api::GetBucketListReply& from(
                static_cast<api::GetBucketListReply&>(fromMsg));
        documentapi::GetBucketListReply& to(
                static_cast<documentapi::GetBucketListReply&>(toMsg));
        const std::vector<api::GetBucketListReply::BucketInfo>& buckets(
                from.getBuckets());
        for (uint32_t i = 0; i < buckets.size(); i++) {
            to.getBuckets().push_back(
                    documentapi::GetBucketListReply::BucketInfo(
                        buckets[i]._bucket, buckets[i]._bucketInformation));
        }
    } else if (toMsg.getType() == DocumentProtocol::REPLY_CREATEVISITOR) {
        api::CreateVisitorReply& from(
                static_cast<api::CreateVisitorReply&>(fromMsg));
        documentapi::CreateVisitorReply& to(
                static_cast<documentapi::CreateVisitorReply&>(toMsg));
        to.setLastBucket(from.getLastBucket());
        to.setVisitorStatistics(from.getVisitorStatistics());
    } else if (toMsg.getType() == DocumentProtocol::REPLY_BATCHDOCUMENTUPDATE) {
        api::BatchDocumentUpdateReply& from(
                static_cast<api::BatchDocumentUpdateReply&>(fromMsg));
        documentapi::BatchDocumentUpdateReply& to(
                static_cast<documentapi::BatchDocumentUpdateReply&>(toMsg));
        to.getDocumentsNotFound() = from.getDocumentsNotFound();
    }
}

} // storage
