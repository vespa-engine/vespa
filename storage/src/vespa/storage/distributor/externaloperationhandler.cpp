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
#include <vespa/storageapi/message/batch.h>
#include <vespa/storageapi/message/stat.h>
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"

#include <vespa/log/log.h>
LOG_SETUP(".distributor.manager");

namespace storage::distributor {

ExternalOperationHandler::ExternalOperationHandler(Distributor& owner, DistributorBucketSpaceRepo& bucketSpaceRepo,
                                                   const MaintenanceOperationGenerator& gen,
                                                   DistributorComponentRegister& compReg)
    : DistributorComponent(owner, bucketSpaceRepo, compReg, "External operation handler"),
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

bool
ExternalOperationHandler::checkTimestampMutationPreconditions(api::StorageCommand& cmd,
                                                              const document::BucketId &bucketId,
                                                              PersistenceOperationMetricSet& persistenceMetrics)
{
    document::Bucket bucket(cmd.getBucket().getBucketSpace(), bucketId);
    if (!checkDistribution(cmd, bucket)) {
        LOG(debug, "Distributor manager received %s, bucket %s with wrong distribution",
            cmd.toString().c_str(), bucket.toString().c_str());

        persistenceMetrics.failures.wrongdistributor++;
        return false;
    }
    if (!checkSafeTimeReached(cmd)) {
        persistenceMetrics.failures.safe_time_not_reached++;
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
    persistenceMetrics.failures.concurrent_mutations++;
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

    if (!checkDistribution(*cmd, bucket)) {
        LOG(debug, "Distributor manager received %s with wrong distribution", cmd->toString().c_str());

        getMetrics().removelocations[cmd->getLoadType()].failures.wrongdistributor++;
        return true;
    }

    _op = std::make_shared<RemoveLocationOperation>(*this, _bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()),
                                                    cmd, getMetrics().removelocations[cmd->getLoadType()]);
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, Get)
{
    document::Bucket bucket(cmd->getBucket().getBucketSpace(), getBucketId(cmd->getDocumentId()));
    if (!checkDistribution(*cmd, bucket)) {
        LOG(debug, "Distributor manager received get for %s, bucket %s with wrong distribution",
            cmd->getDocumentId().toString().c_str(), bucket.toString().c_str());

        getMetrics().gets[cmd->getLoadType()].failures.wrongdistributor++;
        return true;
    }

    _op = std::make_shared<GetOperation>(*this, _bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()),
                                        cmd, getMetrics().gets[cmd->getLoadType()]);
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, StatBucket)
{
    if (!checkDistribution(*cmd, cmd->getBucket())) {
        return true;
    }
    auto &distributorBucketSpace(_bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()));
    _op = std::make_shared<StatBucketOperation>(*this, distributorBucketSpace, cmd);
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, GetBucketList)
{
    if (!checkDistribution(*cmd, cmd->getBucket())) {
        return true;
    }
    auto bucketSpace(cmd->getBucket().getBucketSpace());
    auto &distributorBucketSpace(_bucketSpaceRepo.get(bucketSpace));
    auto &bucketDatabase(distributorBucketSpace.getBucketDatabase());
    _op = std::make_shared<StatBucketListOperation>(bucketDatabase, _operationGenerator, getIndex(), cmd);
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, CreateVisitor)
{
    const DistributorConfiguration& config(getDistributor().getConfig());
    VisitorOperation::Config visitorConfig(config.getMinBucketsPerVisitor(), config.getMaxVisitorsPerNodePerClientVisitor());
    auto &distributorBucketSpace(_bucketSpaceRepo.get(cmd->getBucket().getBucketSpace()));
    _op = Operation::SP(new VisitorOperation(*this, distributorBucketSpace, cmd, visitorConfig, getMetrics().visits[cmd->getLoadType()]));
    return true;
}

}
