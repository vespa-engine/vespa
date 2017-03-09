// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <set>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>
#include <vespa/document/update/documentupdate.h>

namespace document {
class Document;
}

namespace storage {

namespace api {
class UpdateCommand;
class BatchDocumentUpdateCommand;
class CreateBucketReply;
}

namespace distributor {

/*
 * General functional outline:
 *
 * if bucket is consistent and all copies are in sync
 *   send updates directly to nodes
 * else
 *   start safe (slow) path
 *
 * Slow path:
 *
 * send Get for document to update to inconsistent copies
 * if get reply has document
 *   apply updates and send new put
 * else if create-if-non-existing set on update
 *   create new blank document
 *   apply updates and send new put
 * else
 *   reply with not found
 *
 * Note that the above case also implicitly handles the case in which a
 * bucket does not exist.
*/


class TwoPhaseUpdateOperation : public Operation
{
public:
    TwoPhaseUpdateOperation(DistributorComponent& manager,
                            const std::shared_ptr<api::UpdateCommand> & msg,
                            DistributorMetricSet& metrics);
    ~TwoPhaseUpdateOperation();

    void onStart(DistributorMessageSender& sender);

    const char* getName() const { return "twophaseupdate"; }

    std::string getStatus() const { return ""; }

    void onReceive(DistributorMessageSender&,
                   const std::shared_ptr<api::StorageReply>&);

    void onClose(DistributorMessageSender& sender);

    bool canSendHeaderOnly() const;

private:
    enum class SendState {
        NONE_SENT,
        UPDATES_SENT,
        GETS_SENT,
        PUTS_SENT,
    };

    enum class Mode {
        FAST_PATH,
        SLOW_PATH
    };

    void transitionTo(SendState newState);
    const char* stateToString(SendState);

    void sendReply(DistributorMessageSender&,
                   std::shared_ptr<api::StorageReply>&);
    void sendReplyWithResult(DistributorMessageSender&, const api::ReturnCode&);
    void ensureUpdateReplyCreated();

    bool isFastPathPossible() const;
    void startFastPathUpdate(DistributorMessageSender&);
    void startSafePathUpdate(DistributorMessageSender&);
    bool lostBucketOwnershipBetweenPhases() const;
    void sendLostOwnershipTransientErrorReply(DistributorMessageSender&);
    void schedulePutsWithUpdatedDocument(
            std::shared_ptr<document::Document>,
            api::Timestamp,
            DistributorMessageSender&);
    void applyUpdateToDocument(document::Document&) const;
    std::shared_ptr<document::Document> createBlankDocument() const;
    void setUpdatedForTimestamp(api::Timestamp);
    void handleFastPathReceive(DistributorMessageSender&,
                               const std::shared_ptr<api::StorageReply>&);
    void handleSafePathReceive(DistributorMessageSender&,
                               const std::shared_ptr<api::StorageReply>&);
    void handleSafePathReceivedGet(DistributorMessageSender&,
                                   api::GetReply&);
    void handleSafePathReceivedPut(DistributorMessageSender&,
                                   const api::PutReply&);
    bool shouldCreateIfNonExistent() const;
    bool processAndMatchTasCondition(
            DistributorMessageSender& sender,
            const document::Document& candidateDoc);
    bool satisfiesUpdateTimestampConstraint(api::Timestamp) const;
    void addTraceFromReply(const api::StorageReply& reply);
    bool hasTasCondition() const noexcept;
    void replyWithTasFailure(DistributorMessageSender& sender,
                             vespalib::stringref message);

    PersistenceOperationMetricSet& _updateMetric;
    PersistenceOperationMetricSet& _putMetric;
    PersistenceOperationMetricSet& _getMetric;
    std::shared_ptr<api::UpdateCommand> _updateCmd;
    std::shared_ptr<api::StorageReply> _updateReply;
    DistributorComponent& _manager;
    SentMessageMap _sentMessageMap;
    SendState _sendState;
    Mode _mode;
    mbus::TraceNode _trace;
    document::BucketId _updateDocBucketId;
    bool _replySent;
};

}

}


