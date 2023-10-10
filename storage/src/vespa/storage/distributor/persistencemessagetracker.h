// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributor_stripe_component.h"
#include "distributormetricsset.h"
#include "messagetracker.h"
#include <vespa/storage/distributor/operations/cancel_scope.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageapi/messageapi/bucketinfocommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>

namespace storage::distributor {

class PersistenceMessageTracker final : public MessageTracker {
public:
    using ToSend = MessageTracker::ToSend;

    PersistenceMessageTracker(PersistenceOperationMetricSet& metric,
                              std::shared_ptr<api::BucketInfoReply> reply,
                              const DistributorNodeContext& node_ctx,
                              DistributorStripeOperationContext& op_ctx,
                              CancelScope& cancel_scope);
    ~PersistenceMessageTracker();

    void updateDB();
    void updateMetrics();
    [[nodiscard]] bool success() const noexcept { return _success; }
    void fail(MessageSender& sender, const api::ReturnCode& result);

    /**
       Returns the node the reply was from.
    */
    uint16_t receiveReply(MessageSender& sender, api::BucketInfoReply& reply);
    void updateFromReply(MessageSender& sender, api::BucketInfoReply& reply, uint16_t node);
    std::shared_ptr<api::BucketInfoReply>& getReply() { return _reply; }

    /**
       Sends a set of messages that are permissible for early return.
       If early return is enabled, each message batch must be "finished", that is,
       have at most (messages.size() - initial redundancy) messages left in the
       queue and have it's first message be done.
    */
    void queueMessageBatch(std::vector<MessageTracker::ToSend> messages);

    void add_trace_tree_to_reply(vespalib::Trace trace);

private:
    using MessageBatch  = std::vector<uint64_t>;
    using BucketInfoMap = std::map<document::Bucket, std::vector<BucketCopy>>;

    BucketInfoMap                         _remapBucketInfo;
    BucketInfoMap                         _bucketInfo;
    std::vector<MessageBatch>             _messageBatches;
    PersistenceOperationMetricSet&        _metric;
    std::shared_ptr<api::BucketInfoReply> _reply;
    DistributorStripeOperationContext&    _op_ctx;
    mbus::Trace                           _trace;
    framework::MilliSecTimer              _requestTimer;
    CancelScope&                          _cancel_scope;
    uint32_t                              _n_persistence_replies_total;
    uint32_t                              _n_successful_persistence_replies;
    uint8_t                               _priority;
    bool                                  _success;

    enum class PostPruningStatus {
        ReplicasStillPresent,
        NoReplicasPresent
    };

    constexpr static bool still_has_replicas(PostPruningStatus status) {
        return status == PostPruningStatus::ReplicasStillPresent;
    }

    // Returns ReplicasStillPresent iff `bucket_and_replicas` has at least 1 usable entry after pruning,
    // otherwise returns NoReplicasPresent
    [[nodiscard]] static PostPruningStatus prune_cancelled_nodes_if_present(BucketInfoMap& bucket_and_replicas,
                                                                            const CancelScope& cancel_scope);
    [[nodiscard]] bool canSendReplyEarly() const;
    void addBucketInfoFromReply(uint16_t node, const api::BucketInfoReply& reply);
    void logSuccessfulReply(uint16_t node, const api::BucketInfoReply& reply) const;
    [[nodiscard]] bool hasSentReply() const noexcept { return !_reply; }
    [[nodiscard]] bool has_majority_successful_replies() const noexcept;
    [[nodiscard]] bool has_minority_test_and_set_failure() const noexcept;
    void sendReply(MessageSender& sender);
    void updateFailureResult(const api::BucketInfoReply& reply);
    [[nodiscard]] bool node_is_effectively_cancelled(uint16_t node) const noexcept;
    void handleCreateBucketReply(api::BucketInfoReply& reply, uint16_t node);
    void handlePersistenceReply(api::BucketInfoReply& reply, uint16_t node);
    void transfer_trace_state_to_reply();
};

}
