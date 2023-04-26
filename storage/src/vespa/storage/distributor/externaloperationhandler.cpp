// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_space_distribution_context.h"
#include "crypto_uuid_generator.h"
#include "top_level_distributor.h"
#include "distributor_bucket_space.h"
#include "externaloperationhandler.h"
#include "operation_sequencer.h"
#include <vespa/document/base/documentid.h>
#include <vespa/document/util/feed_reject_helper.h>
#include <vespa/storage/common/reindexing_constants.h>
#include <vespa/storage/distributor/operations/external/getoperation.h>
#include <vespa/storage/distributor/operations/external/putoperation.h>
#include <vespa/storage/distributor/operations/external/read_for_write_visitor_operation.h>
#include <vespa/storage/distributor/operations/external/removelocationoperation.h>
#include <vespa/storage/distributor/operations/external/removeoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketlistoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketoperation.h>
#include <vespa/storage/distributor/operations/external/twophaseupdateoperation.h>
#include <vespa/storage/distributor/operations/external/visitoroperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>

#include <vespa/log/log.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

LOG_SETUP(".distributor.manager");

namespace storage::distributor {

class DirectDispatchSender : public DistributorStripeMessageSender {
    DistributorNodeContext& _node_ctx;
    NonTrackingMessageSender& _msg_sender;
public:
    DirectDispatchSender(DistributorNodeContext& node_ctx,
                         NonTrackingMessageSender& msg_sender)
        : _node_ctx(node_ctx),
          _msg_sender(msg_sender)
    {}
    ~DirectDispatchSender() override = default;

    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override {
        _msg_sender.send_up_without_tracking(cmd);
    }
    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override {
        _msg_sender.send_up_without_tracking(reply);
    }
    int getDistributorIndex() const override {
        return _node_ctx.node_index();
    }
    const ClusterContext & cluster_context() const override {
        return _node_ctx;
    }
    PendingMessageTracker& getPendingMessageTracker() override {
        abort(); // Never called by the messages using this component.
    }
    const PendingMessageTracker& getPendingMessageTracker() const override {
        abort(); // Never called by the messages using this component.
    }
    const OperationSequencer& operation_sequencer() const noexcept override {
        abort(); // Never called by the messages using this component.
    }
    OperationSequencer& operation_sequencer() noexcept override {
        abort(); // Never called by the messages using this component.
    }
};

ExternalOperationHandler::ExternalOperationHandler(DistributorNodeContext& node_ctx,
                                                   DistributorStripeOperationContext& op_ctx,
                                                   DistributorMetricSet& metrics,
                                                   ChainedMessageSender& msg_sender,
                                                   OperationSequencer& operation_sequencer,
                                                   NonTrackingMessageSender& non_tracking_sender,
                                                   DocumentSelectionParser& parser,
                                                   const MaintenanceOperationGenerator& gen,
                                                   OperationOwner& operation_owner)
    : _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _metrics(metrics),
      _msg_sender(msg_sender),
      _operation_sequencer(operation_sequencer),
      _parser(parser),
      _direct_dispatch_sender(std::make_unique<DirectDispatchSender>(node_ctx, non_tracking_sender)),
      _operationGenerator(gen),
      _rejectFeedBeforeTimeReached(), // At epoch
      _distributor_operation_owner(operation_owner),
      _non_main_thread_ops_mutex(),
      _non_main_thread_ops_owner(*_direct_dispatch_sender, _node_ctx.clock()),
      _uuid_generator(std::make_unique<CryptoUuidGenerator>()),
      _concurrent_gets_enabled(false),
      _use_weak_internal_read_consistency_for_gets(false)
{
}

ExternalOperationHandler::~ExternalOperationHandler() = default;

bool
ExternalOperationHandler::handleMessage(const std::shared_ptr<api::StorageMessage>& msg, Operation::SP& op)
{
    _op.reset();
    bool retVal = msg->callHandler(*this, msg);
    op = std::move(_op); // Don't maintain any strong refs in _op after we've passed it on.
    return retVal;
}

void ExternalOperationHandler::close_pending() {
    std::lock_guard g(_non_main_thread_ops_mutex);
    // Make sure we drain any pending operations upon close.
    _non_main_thread_ops_owner.onClose();
}

api::ReturnCode
ExternalOperationHandler::makeSafeTimeRejectionResult(TimePoint unsafeTime)
{
    std::ostringstream ss;
    auto now_sec(std::chrono::duration_cast<std::chrono::seconds>(unsafeTime.time_since_epoch()));
    auto future_sec(std::chrono::duration_cast<std::chrono::seconds>(_rejectFeedBeforeTimeReached.time_since_epoch()));
    ss << "Operation received at time " << now_sec.count()
       << ", which is before bucket ownership transfer safe time of "
       << future_sec.count();
    return api::ReturnCode(api::ReturnCode::STALE_TIMESTAMP, ss.str());
}

bool
ExternalOperationHandler::checkSafeTimeReached(api::StorageCommand& cmd)
{
    vespalib::system_time now = _node_ctx.clock().getSystemTime();
    if (now < _rejectFeedBeforeTimeReached) {
        api::StorageReply::UP reply(cmd.makeReply());
        reply->setResult(makeSafeTimeRejectionResult(now));
        _msg_sender.sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
        return false;
    }
    return true;
}

void ExternalOperationHandler::bounce_with_result(api::StorageCommand& cmd, const api::ReturnCode& result) {
    api::StorageReply::UP reply(cmd.makeReply());
    reply->setResult(result);
    _msg_sender.sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
}

void ExternalOperationHandler::bounce_with_feed_blocked(api::StorageCommand& cmd) {
    const auto& feed_block = _op_ctx.cluster_state_bundle().feed_block();
    bounce_with_result(cmd, api::ReturnCode(api::ReturnCode::NO_SPACE,
                                            "External feed is blocked due to resource exhaustion: " +
                                                    feed_block->description()));
}

void ExternalOperationHandler::bounce_with_wrong_distribution(api::StorageCommand& cmd,
                                                              const lib::ClusterState& cluster_state)
{
    // Distributor ownership is equal across bucket spaces, so always send back default space state.
    // This also helps client avoid getting confused by possibly observing different actual
    // (derived) state strings for global/non-global document types for the same state version.
    // Similarly, if we've yet to activate any version at all we send back BUSY instead
    // of a suspiciously empty WrongDistributionReply.
    // TOOD consider NOT_READY instead of BUSY once we're sure this won't cause any other issues.
    if (cluster_state.getVersion() != 0) {
        auto cluster_state_str = cluster_state.toString();
        LOG(debug, "Got %s with wrong distribution, sending back state '%s'",
            cmd.toString().c_str(), cluster_state_str.c_str());
        bounce_with_result(cmd, api::ReturnCode(api::ReturnCode::WRONG_DISTRIBUTION, cluster_state_str));
    } else { // Only valid for empty startup state
        LOG(debug, "Got %s with wrong distribution, but no cluster state activated yet. Sending back BUSY",
            cmd.toString().c_str());
        bounce_with_result(cmd, api::ReturnCode(api::ReturnCode::BUSY, "No cluster state activated yet"));
    }
}

void ExternalOperationHandler::bounce_with_wrong_distribution(api::StorageCommand& cmd) {
    const auto& cluster_state = _op_ctx.bucket_space_repo().get(document::FixedBucketSpaces::default_space()).getClusterState();
    bounce_with_wrong_distribution(cmd, cluster_state);
}

void ExternalOperationHandler::bounce_with_busy_during_state_transition(
        api::StorageCommand& cmd,
        const lib::ClusterState& current_state,
        const lib::ClusterState& pending_state)
{
    auto status_str = vespalib::make_string("Currently pending cluster state transition"
                                            " from version %u to %u",
                                            current_state.getVersion(), pending_state.getVersion());

    api::StorageReply::UP reply(cmd.makeReply());
    api::ReturnCode ret(api::ReturnCode::BUSY, status_str);
    reply->setResult(ret);
    _msg_sender.sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
}

bool
ExternalOperationHandler::checkTimestampMutationPreconditions(api::StorageCommand& cmd,
                                                              const document::BucketId &bucketId,
                                                              PersistenceOperationMetricSet& persistenceMetrics)
{
    auto &bucket_space(_op_ctx.bucket_space_repo().get(cmd.getBucket().getBucketSpace()));
    auto bucket_ownership_flags = bucket_space.get_bucket_ownership_flags(bucketId);
    if (!bucket_ownership_flags.owned_in_current_state()) {
        document::Bucket bucket(cmd.getBucket().getBucketSpace(), bucketId);
        LOG(debug, "Distributor manager received %s, bucket %s with wrong distribution",
            cmd.toString().c_str(), bucket.toString().c_str());
        bounce_with_wrong_distribution(cmd);
        persistenceMetrics.failures.wrongdistributor.inc();
        return false;
    }

    if (!bucket_ownership_flags.owned_in_pending_state()) {
        // We return BUSY here instead of WrongDistributionReply to avoid clients potentially
        // ping-ponging between cluster state versions during a state transition.
        auto& current_state = bucket_space.getClusterState();
        auto& pending_state = bucket_space.get_pending_cluster_state();
        bounce_with_busy_during_state_transition(cmd, current_state, pending_state);
        return false;
    }

    if (!checkSafeTimeReached(cmd)) {
        persistenceMetrics.failures.safe_time_not_reached.inc();
        return false;
    }
    return true;
}

std::shared_ptr<api::StorageMessage>
ExternalOperationHandler::makeConcurrentMutationRejectionReply(api::StorageCommand& cmd,
                                                               const document::DocumentId& docId,
                                                               PersistenceOperationMetricSet& persistenceMetrics) const
{
    auto err_msg = vespalib::make_string("A mutating operation for document '%s' is already in progress",
                                         docId.toString().c_str());
    LOG(debug, "Aborting incoming %s operation: %s", cmd.getType().toString().c_str(), err_msg.c_str());
    persistenceMetrics.failures.concurrent_mutations.inc();
    api::StorageReply::UP reply(cmd.makeReply());
    reply->setResult(api::ReturnCode(api::ReturnCode::BUSY, err_msg));
    return std::shared_ptr<api::StorageMessage>(reply.release());
}

bool ExternalOperationHandler::allowMutation(const SequencingHandle& handle) const {
    const auto& config(_op_ctx.distributor_config());
    if (!config.getSequenceMutatingOperations()) {
        // Sequencing explicitly disabled, so always allow.
        return true;
    }
    return handle.valid();
}

template <typename Func>
void ExternalOperationHandler::bounce_or_invoke_read_only_op(
        api::StorageCommand& cmd,
        const document::Bucket& bucket,
        PersistenceOperationMetricSet& metrics,
        Func func)
{
    auto &bucket_space(_op_ctx.bucket_space_repo().get(bucket.getBucketSpace()));
    auto bucket_ownership_flags = bucket_space.get_bucket_ownership_flags(bucket.getBucketId());
    if (!bucket_ownership_flags.owned_in_current_state()) {
        LOG(debug, "Distributor manager received %s, bucket %s with wrong distribution",
            cmd.toString().c_str(), bucket.toString().c_str());
        bounce_with_wrong_distribution(cmd);
        metrics.failures.wrongdistributor.inc();
        return;
    }

    if (bucket_ownership_flags.owned_in_pending_state()) {
        func(_op_ctx.bucket_space_repo());
    } else {
        if (_op_ctx.distributor_config().allowStaleReadsDuringClusterStateTransitions()) {
            func(_op_ctx.read_only_bucket_space_repo());
        } else {
            auto& current_state = bucket_space.getClusterState();
            auto& pending_state = bucket_space.get_pending_cluster_state();
            bounce_with_busy_during_state_transition(cmd, current_state, pending_state);
        }
    }
}

namespace {

bool put_is_from_reindexing_visitor(const api::PutCommand& cmd) {
    const auto& tas_cond = cmd.getCondition();
    return (tas_cond.isPresent() && (tas_cond.getSelection().starts_with(reindexing_bucket_lock_bypass_prefix())));
}

// Precondition: put_is_from_reindexing_visitor(cmd) == true
std::string extract_reindexing_token(const api::PutCommand& cmd) {
    const auto& tas_str = cmd.getCondition().getSelection();
    auto eq_idx = tas_str.find_first_of('=');
    if (eq_idx != std::string::npos) {
        return tas_str.substr(eq_idx + 1);
    }
    return "";
}

}

bool ExternalOperationHandler::onPut(const std::shared_ptr<api::PutCommand>& cmd) {
    if (_op_ctx.cluster_state_bundle().block_feed_in_cluster()) {
        bounce_with_feed_blocked(*cmd);
        return true;
    }

    auto& metrics = getMetrics().puts;
    if (!checkTimestampMutationPreconditions(*cmd, _op_ctx.make_split_bit_constrained_bucket_id(cmd->getDocumentId()), metrics)) {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(_op_ctx.generate_unique_timestamp());
    }

    const auto bucket_space = cmd->getBucket().getBucketSpace();
    auto handle = _operation_sequencer.try_acquire(bucket_space, cmd->getDocumentId());
    bool allow = allowMutation(handle);
    if (put_is_from_reindexing_visitor(*cmd)) {
        auto expect_token = extract_reindexing_token(*cmd);
        if (!allow && handle.is_blocked_by_bucket()) {
            if (handle.is_bucket_blocked_with_token(expect_token)) {
                cmd->setCondition(documentapi::TestAndSetCondition()); // Must clear TaS or the backend will reject the op
                allow = true;
            } else {
                bounce_with_result(*cmd, api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                                                         "Expected bucket lock token did not match actual lock token"));
                return true;
            }
        } else {
            bounce_with_result(*cmd, api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                                                     "Operation expects a read-for-write bucket lock to be present, "
                                                     "but none currently exists"));
            return true;
        }
    }
    if (allow) {
        _op = std::make_shared<PutOperation>(_node_ctx, _op_ctx,
                                             _op_ctx.bucket_space_repo().get(bucket_space),
                                             std::move(cmd), getMetrics().puts, std::move(handle));
    } else {
        _msg_sender.sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId(), metrics));
    }

    return true;
}


bool ExternalOperationHandler::onUpdate(const std::shared_ptr<api::UpdateCommand>& cmd) {
    if (_op_ctx.cluster_state_bundle().block_feed_in_cluster() &&
            document::FeedRejectHelper::mustReject(*cmd->getUpdate()))
    {
        bounce_with_feed_blocked(*cmd);
        return true;
    }

    auto& metrics = getMetrics().updates;
    if (!checkTimestampMutationPreconditions(*cmd, _op_ctx.make_split_bit_constrained_bucket_id(cmd->getDocumentId()), metrics)) {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(_op_ctx.generate_unique_timestamp());
    }
    const auto bucket_space = cmd->getBucket().getBucketSpace();
    auto handle = _operation_sequencer.try_acquire(bucket_space, cmd->getDocumentId());
    if (allowMutation(handle)) {
        _op = std::make_shared<TwoPhaseUpdateOperation>(_node_ctx, _op_ctx, _parser,
                                                        _op_ctx.bucket_space_repo().get(bucket_space),
                                                        std::move(cmd), getMetrics(), std::move(handle));
    } else {
        _msg_sender.sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId(), metrics));
    }

    return true;
}


bool ExternalOperationHandler::onRemove(const std::shared_ptr<api::RemoveCommand>& cmd) {
    auto& metrics = getMetrics().removes;
    if (!checkTimestampMutationPreconditions(*cmd, _op_ctx.make_split_bit_constrained_bucket_id(cmd->getDocumentId()), metrics)) {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(_op_ctx.generate_unique_timestamp());
    }
    const auto bucket_space = cmd->getBucket().getBucketSpace();
    auto handle = _operation_sequencer.try_acquire(bucket_space, cmd->getDocumentId());
    if (allowMutation(handle)) {
        auto &distributorBucketSpace(_op_ctx.bucket_space_repo().get(bucket_space));

        _op = std::make_shared<RemoveOperation>(_node_ctx, _op_ctx, distributorBucketSpace, std::move(cmd),
                                                getMetrics().removes, std::move(handle));
    } else {
        _msg_sender.sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId(), metrics));
    }

    return true;
}

bool ExternalOperationHandler::onRemoveLocation(const std::shared_ptr<api::RemoveLocationCommand>& cmd) {
    document::BucketId bid;
    RemoveLocationOperation::getBucketId(_node_ctx, _parser, *cmd, bid);
    document::Bucket bucket(cmd->getBucket().getBucketSpace(), bid);

    auto& metrics = getMetrics().removelocations;
    if (!checkTimestampMutationPreconditions(*cmd, bucket.getBucketId(), metrics)) {
        return true;
    }

    _op = std::make_shared<RemoveLocationOperation>(_node_ctx, _op_ctx, _parser,
                                                    _op_ctx.bucket_space_repo().get(cmd->getBucket().getBucketSpace()),
                                                    std::move(cmd), getMetrics().removelocations);
    return true;
}

api::InternalReadConsistency ExternalOperationHandler::desired_get_read_consistency() const noexcept {
    return (use_weak_internal_read_consistency_for_gets()
            ? api::InternalReadConsistency::Weak
            : api::InternalReadConsistency::Strong);
}

std::shared_ptr<Operation> ExternalOperationHandler::try_generate_get_operation(const std::shared_ptr<api::GetCommand>& cmd) {
    document::Bucket bucket(cmd->getBucket().getBucketSpace(), _op_ctx.make_split_bit_constrained_bucket_id(cmd->getDocumentId()));
    auto& metrics = getMetrics().gets;
    auto snapshot = _op_ctx.read_snapshot_for_bucket(bucket);
    if (!snapshot.is_routable()) {
        const auto& ctx = snapshot.context();
        if (ctx.has_pending_state_transition()) {
            bounce_with_busy_during_state_transition(*cmd, *ctx.default_active_cluster_state(),
                                                     *ctx.pending_cluster_state());
        } else {
            bounce_with_wrong_distribution(*cmd, *snapshot.context().default_active_cluster_state());
            metrics.locked()->failures.wrongdistributor.inc();
        }
        return {};
    }
    // The snapshot is aware of whether stale reads are enabled, so we don't have to check that here.
    const auto* space_repo = snapshot.bucket_space_repo();
    assert(space_repo != nullptr);
    return std::make_shared<GetOperation>(_node_ctx, space_repo->get(bucket.getBucketSpace()),
                                          snapshot.steal_read_guard(), cmd, metrics,
                                          desired_get_read_consistency());
}

bool ExternalOperationHandler::onGet(const std::shared_ptr<api::GetCommand>& cmd) {
    _op = try_generate_get_operation(cmd);
    return true;
}

bool ExternalOperationHandler::onStatBucket(const std::shared_ptr<api::StatBucketCommand>& cmd) {
    auto& metrics = getMetrics().stats;
    bounce_or_invoke_read_only_op(*cmd, cmd->getBucket(), metrics, [&](auto& bucket_space_repo) {
        auto& bucket_space = bucket_space_repo.get(cmd->getBucket().getBucketSpace());
        _op = std::make_shared<StatBucketOperation>(bucket_space, cmd);
    });
    return true;
}

bool ExternalOperationHandler::onGetBucketList(const std::shared_ptr<api::GetBucketListCommand>& cmd) {
    auto& metrics = getMetrics().getbucketlists;
    bounce_or_invoke_read_only_op(*cmd, cmd->getBucket(), metrics, [&](auto& bucket_space_repo) {
        auto& bucket_space = bucket_space_repo.get(cmd->getBucket().getBucketSpace());
        auto& bucket_database = bucket_space.getBucketDatabase();
        _op = std::make_shared<StatBucketListOperation>(bucket_database, _operationGenerator, _node_ctx.node_index(), cmd);
    });
    return true;
}

bool ExternalOperationHandler::onCreateVisitor(const std::shared_ptr<api::CreateVisitorCommand>& cmd) {
    // TODO same handling as Gets (VisitorOperation needs to change)
    const auto& config(_op_ctx.distributor_config());
    VisitorOperation::Config visitorConfig(config.getMinBucketsPerVisitor(), config.getMaxVisitorsPerNodePerClientVisitor());
    auto &distributorBucketSpace(_op_ctx.bucket_space_repo().get(cmd->getBucket().getBucketSpace()));
    auto visit_op = std::make_shared<VisitorOperation>(_node_ctx, _op_ctx, distributorBucketSpace, cmd, visitorConfig, getMetrics().visits);
    if (visit_op->is_read_for_write()) {
        _op = std::make_shared<ReadForWriteVisitorOperationStarter>(std::move(visit_op), _operation_sequencer,
                                                                    _distributor_operation_owner,
                                                                    _op_ctx.pending_message_tracker(),
                                                                    *_uuid_generator);
    } else {
        _op = std::move(visit_op);
    }
    return true;
}

bool ExternalOperationHandler::try_handle_message_outside_main_thread(const std::shared_ptr<api::StorageMessage>& msg) {
    const auto type_id = msg->getType().getId();
    if (type_id == api::MessageType::GET_ID) {
        // Only do this check for Get _requests_ to avoid the following case:
        //  1) Stale reads are initially enabled and a Get request is received
        //  2) A Get is sent to the content node(s)
        //  3) Stale reads are disabled via config
        //  4) Get-reply from content node is disregarded since concurrent reads are no longer allowed
        //  5) We've effectively leaked a Get operation, and the client will time out
        // TODO consider having stale reads _not_ be a live config instead!
        if (!concurrent_gets_enabled()) {
            return false;
        }
        auto op = try_generate_get_operation(std::dynamic_pointer_cast<api::GetCommand>(msg));
        if (op) {
            std::lock_guard g(_non_main_thread_ops_mutex);
            _non_main_thread_ops_owner.start(std::move(op), msg->getPriority());
        }
        return true;
    } else if (type_id == api::MessageType::GET_REPLY_ID) {
        std::lock_guard g(_non_main_thread_ops_mutex);
        // The Get for which this reply was created may have been sent by someone outside
        // the ExternalOperationHandler, such as TwoPhaseUpdateOperation. Pass it on if so.
        // It is undefined which thread actually invokes this, so mutex protection of reply
        // handling is crucial!
        return _non_main_thread_ops_owner.handleReply(std::dynamic_pointer_cast<api::StorageReply>(msg));
    }
    return false;
}

}
