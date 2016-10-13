// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/distributorcomponent.h>
#include <vespa/storage/distributor/messagetracker.h>
#include <vespa/storageapi/messageapi/bucketinfocommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>


namespace storage {

namespace distributor {

struct PersistenceMessageTracker {
    virtual ~PersistenceMessageTracker() { }
    typedef MessageTracker::ToSend ToSend;

    virtual void fail(MessageSender&, const api::ReturnCode&) = 0;
    virtual void queueMessageBatch(const std::vector<ToSend>&) = 0;
    virtual uint16_t receiveReply(MessageSender&, api::BucketInfoReply&) = 0;
    virtual std::shared_ptr<api::BucketInfoReply>& getReply() = 0;
    virtual void updateFromReply(MessageSender&, api::BucketInfoReply&,
                                 uint16_t node) = 0;

    virtual void queueCommand(api::BucketCommand::SP, uint16_t target) = 0;
    virtual void flushQueue(MessageSender&) = 0;
    virtual uint16_t handleReply(api::BucketReply& reply) = 0;
};

class PersistenceMessageTrackerImpl : public PersistenceMessageTracker,
                                      public MessageTracker
{
private:
    typedef std::map<document::BucketId, std::vector<BucketCopy> > BucketInfoMap;
    BucketInfoMap _remapBucketInfo;
    BucketInfoMap _bucketInfo;

public:
    PersistenceMessageTrackerImpl(PersistenceOperationMetricSet& metric,
                                  std::shared_ptr<api::BucketInfoReply> reply,
                                  DistributorComponent&,
                                  api::Timestamp revertTimestamp = 0);

    void updateDB();

    void updateMetrics();

    bool success() const { return _success; }

    void fail(MessageSender& sender, const api::ReturnCode& result);

    /**
       Returns the node the reply was from.
    */
    uint16_t receiveReply(MessageSender& sender, api::BucketInfoReply& reply);

    void updateFromReply(MessageSender& sender, api::BucketInfoReply& reply, uint16_t node);

    std::shared_ptr<api::BucketInfoReply>& getReply() { return _reply; }

    typedef std::pair<document::BucketId, uint16_t> BucketNodePair;

    void revert(MessageSender& sender, const std::vector<BucketNodePair> revertNodes);

    /**
       Sends a set of messages that are permissible for early return.
       If early return is enabled, each message batch must be "finished", that is,
       have at most (messages.size() - initial redundancy) messages left in the
       queue and have it's first message be done.
    */
    void queueMessageBatch(const std::vector<MessageTracker::ToSend>& messages);

private:
    typedef std::vector<uint64_t> MessageBatch;
    std::vector<MessageBatch> _messageBatches;

    PersistenceOperationMetricSet& _metric;
    std::shared_ptr<api::BucketInfoReply> _reply;
    DistributorComponent& _manager;
    FastOS_Time _creationTime;
    bool _success;
    api::Timestamp _revertTimestamp;
    std::vector<std::pair<document::BucketId, uint16_t> > _revertNodes;
    mbus::TraceNode _trace;
    uint8_t _priority;

    bool canSendReplyEarly() const;
    void addBucketInfoFromReply(uint16_t node,
                                const api::BucketInfoReply& reply);
    void logSuccessfulReply(uint16_t node,
                            const api::BucketInfoReply& reply) const;
    bool hasSentReply() const {
        return _reply.get() == 0;
    }
    bool shouldRevert() const;
    void sendReply(MessageSender& sender);
    void checkCopiesDeleted();
    void updateFailureResult(const api::BucketInfoReply& reply);
    void handleCreateBucketReply(
            api::BucketInfoReply& reply,
            uint16_t node);
    void handlePersistenceReply(
            api::BucketInfoReply& reply,
            uint16_t node);

    virtual void queueCommand(std::shared_ptr<api::BucketCommand> msg,
                              uint16_t target)
        { MessageTracker::queueCommand(msg, target); }
    virtual void flushQueue(MessageSender& s) { MessageTracker::flushQueue(s); }
    virtual uint16_t handleReply(api::BucketReply& r)
        { return MessageTracker::handleReply(r); }
};

}

}

