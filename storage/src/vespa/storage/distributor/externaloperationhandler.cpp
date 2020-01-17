// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_space_distribution_context.h"
#include "externaloperationhandler.h"
#include "distributor.h"
#include <vespa/document/base/documentid.h>
#include <vespa/storage/distributor/operations/external/putoperation.h>
#include <vespa/storage/distributor/operations/external/twophaseupdateoperation.h>
#include <vespa/storage/distributor/operations/external/updateoperation.h>
#include <vespa/storage/distributor/operations/external/removeoperation.h>
#include <vespa/storage/distributor/operations/external/getoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketlistoperation.h>
#include <vespa/storage/distributor/operations/external/removelocationoperation.h>
#include <vespa/storage/distributor/operations/external/visitoroperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"

#include <vespa/log/log.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

LOG_SETUP(".distributor.manager");

namespace storage::distributor {

class DirectDispatchSender : public DistributorMessageSender {
    Distributor& _distributor;
public:
    explicit DirectDispatchSender(Distributor& distributor)
        : _distributor(distributor)
    {}
    ~DirectDispatchSender() override = default;

    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override {
        _distributor.send_up_without_tracking(cmd);
    }
    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override {
        _distributor.send_up_without_tracking(reply);
    }
    int getDistributorIndex() const override {
        return _distributor.getDistributorIndex(); // Thread safe
    }
    const std::string& getClusterName() const override {
        return _distributor.getClusterName(); // Thread safe
    }
    const PendingMessageTracker& getPendingMessageTracker() const override {
        abort(); // Never called by the messages using this component.
    }
};

ExternalOperationHandler::ExternalOperationHandler(Distributor& owner,
                                                   DistributorBucketSpaceRepo& bucketSpaceRepo,
                                                   DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
                                                   const MaintenanceOperationGenerator& gen,
                                                   DistributorComponentRegister& compReg)
    : DistributorComponent(owner, bucketSpaceRepo, readOnlyBucketSpaceRepo, compReg, "External operation handler"),
      _direct_dispatch_sender(std::make_unique<DirectDispatchSender>(owner)),
      _operationGenerator(gen),
      _rejectFeedBeforeTimeReached(), // At epoch
      _non_main_thread_ops_mutex(),
      _non_main_thread_ops_owner(*_direct_dispatch_sender, getClock()),
      _concurrent_gets_enabled(false),
      _use_weak_internal_read_consistency_for_gets(false)
{
}

ExternalOperationHandler::~ExternalOperationHandler() = default;

bool
ExternalOperationHandler::handleMessage(const std::shared_ptr<api::StorageMessage>& msg, Operation::SP& op)
{
    _op = Operation::SP();
    bool retVal = msg->callHandler(*this, msg);
    op = _op;
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
    const auto now = TimePoint(std::chrono::seconds(getClock().getTimeInSeconds().getTime()));
    if (now < _rejectFeedBeforeTimeReached) {
        api::StorageReply::UP reply(cmd.makeReply());
        reply->setResult(makeSafeTimeRejectionResult(now));
        sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
        return false;
    }
    return true;
}

void ExternalOperationHandler::bounce_with_result(api::StorageCommand& cmd, const api::ReturnCode& result) {
    api::StorageReply::UP reply(cmd.makeReply());
    reply->setResult(result);
    sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
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
    const auto& cluster_state = _bucketSpaceRepo.get(document::FixedBucketSpaces::default_space()).getClusterState();
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
    sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
}

bool
ExternalOperationHandler::checkTimestampMutationPreconditions(api::StorageCommand& cmd,
                                                              const document::BucketId &bucketId,
                                                              PersistenceOperationMetricSet& persistenceMetrics)
{
    document::Bucket bucket(cmd.getBucket().getBucketSpace(), bucketId);
    if (!ownsBucketInCurrentState(bucket)) {
        LOG(debug, "Distributor manager received %s, bucket %s with wrong distribution",
            cmd.toString().c_str(), bucket.toString().c_str());
        bounce_with_wrong_distribution(cmd);
        persistenceMetrics.failures.wrongdistributor.inc();
        return false;
    }

    auto pending = getDistributor().checkOwnershipInPendingState(bucket);
    if (!pending.isOwned()) {
        // We return BUSY here instead of WrongDistributionReply to avoid clients potentially
        // ping-ponging between cluster state versions during a state transition.
        auto& current_state = _bucketSpaceRepo.get(document::FixedBucketSpaces::default_space()).getClusterState();
        auto& pending_state = pending.getNonOwnedState();
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
    const auto& config(getDistributor().getConfig());
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
    if (!ownsBucketInCurrentState(bucket)) {
        LOG(debug, "Distributor manager received %s, bucket %s with wrong distribution",
            cmd.toString().c_str(), bucket.toString().c_str());
        bounce_with_wrong_distribution(cmd);
        metrics.failures.wrongdistributor.inc();
        return;
    }

    auto pending = getDistributor().checkOwnershipInPendingState(bucket);
    if (pending.isOwned()) {
        func(_bucketSpaceRepo);
    } else {
        if (getDistributor().getConfig().allowStaleReadsDuringClusterStateTransitions()) {
            func(_readOnlyBucketSpaceRepo);
        } else {
            auto& current_state = _bucketSpaceRepo.get(document::FixedBucketSpaces::default_space()).getClusterState();
            auto& pending_state = pending.getNonOwnedState();
            bounce_with_busy_during_state_transition(cmd, current_state, pending_state);
        }
    }
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, Put)
{
    auto& metrics = getMetrics().puts[cmd->getLoadType()];
    if (!checkTimestampMutationPreconditions(*cmd, getBucketId(cmd->getDocumentId()), metrics)) {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(getUniqueTimestamp());
    }

    auto handle = _mutationSequencer.try_acquire(cmd->getDocumentId());
    if (allowMutation(handle)) {
        _op = std::make_shared<PutOperation>(*this,
                                             _bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()),
                                             cmd, getMetrics().puts[cmd->getLoadType()], std::move(handle));
    } else {
        sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId(), metrics));
    }

    return true;
}


IMPL_MSG_COMMAND_H(ExternalOperationHandler, Update)
{
    auto& metrics = getMetrics().updates[cmd->getLoadType()];
    if (!checkTimestampMutationPreconditions(*cmd, getBucketId(cmd->getDocumentId()), metrics)) {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(getUniqueTimestamp());
    }
    auto handle = _mutationSequencer.try_acquire(cmd->getDocumentId());
    if (allowMutation(handle)) {
        _op = std::make_shared<TwoPhaseUpdateOperation>(*this,
                                                        _bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()),
                                                        cmd, getMetrics(), std::move(handle));
    } else {
        sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId(), metrics));
    }

    return true;
}


IMPL_MSG_COMMAND_H(ExternalOperationHandler, Remove)
{
    auto& metrics = getMetrics().removes[cmd->getLoadType()];
    if (!checkTimestampMutationPreconditions(*cmd, getBucketId(cmd->getDocumentId()), metrics)) {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(getUniqueTimestamp());
    }
    auto handle = _mutationSequencer.try_acquire(cmd->getDocumentId());
    if (allowMutation(handle)) {
        auto &distributorBucketSpace(_bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()));
        _op = std::make_shared<RemoveOperation>(*this, distributorBucketSpace, cmd,
                                                getMetrics().removes[cmd->getLoadType()], std::move(handle));
    } else {
        sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId(), metrics));
    }

    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, RemoveLocation)
{
    document::BucketId bid;
    RemoveLocationOperation::getBucketId(*this, *cmd, bid);
    document::Bucket bucket(cmd->getBucket().getBucketSpace(), bid);

    auto& metrics = getMetrics().removelocations[cmd->getLoadType()];
    if (!checkTimestampMutationPreconditions(*cmd, bucket.getBucketId(), metrics)) {
        return true;
    }

    _op = std::make_shared<RemoveLocationOperation>(*this, _bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()),
                                                    cmd, getMetrics().removelocations[cmd->getLoadType()]);
    return true;
}

api::InternalReadConsistency ExternalOperationHandler::desired_get_read_consistency() const noexcept {
    return (use_weak_internal_read_consistency_for_gets()
            ? api::InternalReadConsistency::Weak
            : api::InternalReadConsistency::Strong);
}

std::shared_ptr<Operation> ExternalOperationHandler::try_generate_get_operation(const std::shared_ptr<api::GetCommand>& cmd) {
    document::Bucket bucket(cmd->getBucket().getBucketSpace(), getBucketId(cmd->getDocumentId()));
    auto& metrics = getMetrics().gets[cmd->getLoadType()];
    auto snapshot = getDistributor().read_snapshot_for_bucket(bucket);
    if (!snapshot.is_routable()) {
        const auto& ctx = snapshot.context();
        if (ctx.has_pending_state_transition()) {
            bounce_with_busy_during_state_transition(*cmd, *ctx.default_active_cluster_state(),
                                                     *ctx.pending_cluster_state());
        } else {
            bounce_with_wrong_distribution(*cmd, *snapshot.context().default_active_cluster_state());
            metrics.locked()->failures.wrongdistributor.inc();
        }
        return std::shared_ptr<Operation>();
    }
    // The snapshot is aware of whether stale reads are enabled, so we don't have to check that here.
    const auto* space_repo = snapshot.bucket_space_repo();
    assert(space_repo != nullptr);
    return std::make_shared<GetOperation>(*this, space_repo->get(bucket.getBucketSpace()),
                                          snapshot.steal_read_guard(), cmd, metrics,
                                          desired_get_read_consistency());
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, Get)
{
    _op = try_generate_get_operation(cmd);
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, StatBucket)
{
    auto& metrics = getMetrics().stats[cmd->getLoadType()];
    bounce_or_invoke_read_only_op(*cmd, cmd->getBucket(), metrics, [&](auto& bucket_space_repo) {
        auto& bucket_space = bucket_space_repo.get(cmd->getBucket().getBucketSpace());
        _op = std::make_shared<StatBucketOperation>(*this, bucket_space, cmd);
    });
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, GetBucketList)
{
    auto& metrics = getMetrics().getbucketlists[cmd->getLoadType()];
    bounce_or_invoke_read_only_op(*cmd, cmd->getBucket(), metrics, [&](auto& bucket_space_repo) {
        auto& bucket_space = bucket_space_repo.get(cmd->getBucket().getBucketSpace());
        auto& bucket_database = bucket_space.getBucketDatabase();
        _op = std::make_shared<StatBucketListOperation>(bucket_database, _operationGenerator, getIndex(), cmd);
    });
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, CreateVisitor)
{
    // TODO same handling as Gets (VisitorOperation needs to change)
    const DistributorConfiguration& config(getDistributor().getConfig());
    VisitorOperation::Config visitorConfig(config.getMinBucketsPerVisitor(), config.getMaxVisitorsPerNodePerClientVisitor());
    auto &distributorBucketSpace(_bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()));
    _op = Operation::SP(new VisitorOperation(*this, distributorBucketSpace, cmd, visitorConfig, getMetrics().visits[cmd->getLoadType()]));
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
