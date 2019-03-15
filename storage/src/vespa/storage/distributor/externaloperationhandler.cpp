// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/document/util/stringutil.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"

#include <vespa/log/log.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

LOG_SETUP(".distributor.manager");

namespace storage::distributor {

ExternalOperationHandler::ExternalOperationHandler(Distributor& owner,
                                                   DistributorBucketSpaceRepo& bucketSpaceRepo,
                                                   DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
                                                   const MaintenanceOperationGenerator& gen,
                                                   DistributorComponentRegister& compReg)
    : DistributorComponent(owner, bucketSpaceRepo, readOnlyBucketSpaceRepo, compReg, "External operation handler"),
      _operationGenerator(gen),
      _rejectFeedBeforeTimeReached() // At epoch
{ }

ExternalOperationHandler::~ExternalOperationHandler() = default;

bool
ExternalOperationHandler::handleMessage(const std::shared_ptr<api::StorageMessage>& msg, Operation::SP& op)
{
    _op = Operation::SP();
    bool retVal = msg->callHandler(*this, msg);
    op = _op;
    return retVal;
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

void ExternalOperationHandler::bounce_with_wrong_distribution(api::StorageCommand& cmd) {
    // Distributor ownership is equal across cluster states, so always send back default state.
    // This also helps client avoid getting confused by possibly observing different actual
    // (derived) state strings for global/non-global document types for the same state version.
    // Similarly, if we've yet to activate any version at all we send back BUSY instead
    // of a suspiciously empty WrongDistributionReply.
    const auto& cluster_state = _bucketSpaceRepo.get(document::FixedBucketSpaces::default_space()).getClusterState();
    if (cluster_state.getVersion() != 0) {
        auto cluster_state_str = cluster_state.toString();
        LOG(debug, "Got message with wrong distribution, sending back state '%s'", cluster_state_str.c_str());
        bounce_with_result(cmd, api::ReturnCode(api::ReturnCode::WRONG_DISTRIBUTION, cluster_state_str));
    } else { // Only valid for empty startup state
        LOG(debug, "Got message with wrong distribution, but no cluster state activated yet. Sending back BUSY");
        bounce_with_result(cmd, api::ReturnCode(api::ReturnCode::BUSY, "No cluster state activated yet"));
    }
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

IMPL_MSG_COMMAND_H(ExternalOperationHandler, Get)
{
    document::Bucket bucket(cmd->getBucket().getBucketSpace(), getBucketId(cmd->getDocumentId()));
    auto& metrics = getMetrics().gets[cmd->getLoadType()];
    bounce_or_invoke_read_only_op(*cmd, bucket, metrics, [&](auto& bucket_space_repo) {
        _op = std::make_shared<GetOperation>(*this, bucket_space_repo.get(cmd->getBucket().getBucketSpace()),
                                             cmd, metrics);
    });
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

}
