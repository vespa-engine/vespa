// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/storage/distributor/distributorcomponent.h>
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <atomic>
#include <chrono>
#include <mutex>

namespace documentapi { class TestAndSetCondition; }
namespace storage::lib { class ClusterState; }
namespace storage { class PersistenceOperationMetricSet; }

namespace storage::distributor {

class DistributorMetricSet;
class Distributor;
class MaintenanceOperationGenerator;
class DirectDispatchSender;
class SequencingHandle;
class OperationSequencer;
class OperationOwner;
class UuidGenerator;

class ExternalOperationHandler : public api::MessageHandler
{
public:
    using Clock = std::chrono::system_clock;
    using TimePoint = std::chrono::time_point<Clock>;

    bool onGet(const std::shared_ptr<api::GetCommand>&) override;
    bool onPut(const std::shared_ptr<api::PutCommand>&) override;
    bool onUpdate(const std::shared_ptr<api::UpdateCommand>&) override;
    bool onRemove(const std::shared_ptr<api::RemoveCommand>&) override;
    bool onRemoveLocation(const std::shared_ptr<api::RemoveLocationCommand>&) override;
    bool onStatBucket(const std::shared_ptr<api::StatBucketCommand>&) override;
    bool onCreateVisitor(const std::shared_ptr<api::CreateVisitorCommand>&) override;
    bool onGetBucketList(const std::shared_ptr<api::GetBucketListCommand>&) override;

    ExternalOperationHandler(DistributorNodeContext& node_ctx,
                             DistributorOperationContext& op_ctx,
                             DistributorMetricSet& metrics,
                             ChainedMessageSender& msg_sender,
                             OperationSequencer& operation_sequencer,
                             NonTrackingMessageSender& non_tracking_sender,
                             DocumentSelectionParser& parser,
                             const MaintenanceOperationGenerator& gen,
                             OperationOwner& operation_owner);

    ~ExternalOperationHandler() override;

    bool handleMessage(const std::shared_ptr<api::StorageMessage>& msg,
                       Operation::SP& operation);

    void rejectFeedBeforeTimeReached(TimePoint timePoint) noexcept {
        _rejectFeedBeforeTimeReached = timePoint;
    }

    // Returns true iff message was handled and should not be processed further by the caller.
    bool try_handle_message_outside_main_thread(const std::shared_ptr<api::StorageMessage>& msg);

    void close_pending();

    void set_concurrent_gets_enabled(bool enabled) noexcept {
        _concurrent_gets_enabled.store(enabled, std::memory_order_relaxed);
    }

    bool concurrent_gets_enabled() const noexcept {
        return _concurrent_gets_enabled.load(std::memory_order_relaxed);
    }

    void set_use_weak_internal_read_consistency_for_gets(bool use_weak) noexcept {
        _use_weak_internal_read_consistency_for_gets.store(use_weak, std::memory_order_relaxed);
    }

    bool use_weak_internal_read_consistency_for_gets() const noexcept {
        return _use_weak_internal_read_consistency_for_gets.load(std::memory_order_relaxed);
    }

    // Exposed for testing
    OperationSequencer& operation_sequencer() noexcept {
        return _operation_sequencer;
    }

private:
    DistributorNodeContext& _node_ctx;
    DistributorOperationContext& _op_ctx;
    DistributorMetricSet& _metrics;
    ChainedMessageSender& _msg_sender;
    OperationSequencer& _operation_sequencer;
    DocumentSelectionParser& _parser;
    std::unique_ptr<DirectDispatchSender> _direct_dispatch_sender;
    const MaintenanceOperationGenerator& _operationGenerator;
    Operation::SP _op;
    TimePoint _rejectFeedBeforeTimeReached;
    OperationOwner& _distributor_operation_owner;
    mutable std::mutex _non_main_thread_ops_mutex;
    OperationOwner _non_main_thread_ops_owner;
    std::unique_ptr<UuidGenerator> _uuid_generator;
    std::atomic<bool> _concurrent_gets_enabled;
    std::atomic<bool> _use_weak_internal_read_consistency_for_gets;

    template <typename Func>
    void bounce_or_invoke_read_only_op(api::StorageCommand& cmd,
                                       const document::Bucket& bucket,
                                       PersistenceOperationMetricSet& metrics,
                                       Func f);

    void bounce_with_wrong_distribution(api::StorageCommand& cmd, const lib::ClusterState& cluster_state);
    // Bounce with the current _default_ space cluster state.
    void bounce_with_wrong_distribution(api::StorageCommand& cmd);
    void bounce_with_busy_during_state_transition(api::StorageCommand& cmd,
                                                  const lib::ClusterState& current_state,
                                                  const lib::ClusterState& pending_state);
    void bounce_with_result(api::StorageCommand& cmd, const api::ReturnCode& result);
    void bounce_with_feed_blocked(api::StorageCommand& cmd);
    std::shared_ptr<Operation> try_generate_get_operation(const std::shared_ptr<api::GetCommand>&);

    bool checkSafeTimeReached(api::StorageCommand& cmd);
    api::ReturnCode makeSafeTimeRejectionResult(TimePoint unsafeTime);
    bool checkTimestampMutationPreconditions(
            api::StorageCommand& cmd,
            const document::BucketId &bucketId,
            PersistenceOperationMetricSet& persistenceMetrics);
    std::shared_ptr<api::StorageMessage> makeConcurrentMutationRejectionReply(
            api::StorageCommand& cmd,
            const document::DocumentId& docId,
            PersistenceOperationMetricSet& persistenceMetrics) const;
    bool allowMutation(const SequencingHandle& handle) const;

    api::InternalReadConsistency desired_get_read_consistency() const noexcept;

    DistributorMetricSet& getMetrics() { return _metrics; }
};

}
