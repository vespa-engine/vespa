// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "externaloperationhandler.h"
#include "distributor.h"
#include <vespa/document/base/documentid.h>
#include <vespa/storage/distributor/operations/external/putoperation.h>
#include <vespa/storage/distributor/operations/external/twophaseupdateoperation.h>
#include <vespa/storage/distributor/operations/external/updateoperation.h>
#include <vespa/storage/distributor/operations/external/removeoperation.h>
#include <vespa/storage/distributor/operations/external/getoperation.h>
#include <vespa/storage/distributor/operations/external/multioperationoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketlistoperation.h>
#include <vespa/storage/distributor/operations/external/removelocationoperation.h>
#include <vespa/storage/distributor/operations/external/visitoroperation.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/storageapi/message/stat.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.manager");

namespace storage {
namespace distributor {

ExternalOperationHandler::ExternalOperationHandler(
        Distributor& owner,
        ManagedBucketSpace& bucketSpace,
        const MaintenanceOperationGenerator& gen,
        DistributorComponentRegister& compReg)
    : ManagedBucketSpaceComponent(owner, bucketSpace, compReg, "External operation handler"),
      _operationGenerator(gen),
      _rejectFeedBeforeTimeReached() // At epoch
{ }

ExternalOperationHandler::~ExternalOperationHandler() { }

bool
ExternalOperationHandler::handleMessage(
        const std::shared_ptr<api::StorageMessage>& msg,
        Operation::SP& op)
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
    auto now_sec(std::chrono::duration_cast<std::chrono::seconds>(
                unsafeTime.time_since_epoch()));
    auto future_sec(std::chrono::duration_cast<std::chrono::seconds>(
                _rejectFeedBeforeTimeReached.time_since_epoch()));
    ss << "Operation received at time " << now_sec.count()
       << ", which is before bucket ownership transfer safe time of "
       << future_sec.count();
    return api::ReturnCode(api::ReturnCode::STALE_TIMESTAMP, ss.str());
}

bool
ExternalOperationHandler::checkSafeTimeReached(api::StorageCommand& cmd)
{
    const auto now = TimePoint(std::chrono::seconds(
            getClock().getTimeInSeconds().getTime()));
    if (now < _rejectFeedBeforeTimeReached) {
        api::StorageReply::UP reply(cmd.makeReply());
        reply->setResult(makeSafeTimeRejectionResult(now));
        sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
        return false;
    }
    return true;
}

bool
ExternalOperationHandler::checkTimestampMutationPreconditions(
        api::StorageCommand& cmd,
        const document::BucketId& bucket,
        PersistenceOperationMetricSet& persistenceMetrics)
{
    if (!checkDistribution(cmd, bucket)) {
        LOG(debug,
            "Distributor manager received %s, bucket %s with wrong "
            "distribution",
            cmd.toString().c_str(),
            bucket.toString().c_str());

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
ExternalOperationHandler::makeConcurrentMutationRejectionReply(
        api::StorageCommand& cmd,
        const document::DocumentId& docId) const {
    api::StorageReply::UP reply(cmd.makeReply());
    reply->setResult(api::ReturnCode(
            api::ReturnCode::BUSY, vespalib::make_string(
                    "A mutating operation for document '%s' is already in progress",
                    docId.toString().c_str())));
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
    if (!checkTimestampMutationPreconditions(
            *cmd, getBucketId(cmd->getDocumentId()),
            getMetrics().puts[cmd->getLoadType()]))
    {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(getUniqueTimestamp());
    }

    auto handle = _mutationSequencer.try_acquire(cmd->getDocumentId());
    if (allowMutation(handle)) {
        _op = std::make_shared<PutOperation>(*this, cmd, getMetrics().puts[cmd->getLoadType()], std::move(handle));
    } else {
        sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId()));
    }

    return true;
}


IMPL_MSG_COMMAND_H(ExternalOperationHandler, Update)
{
    if (!checkTimestampMutationPreconditions(
            *cmd, getBucketId(cmd->getDocumentId()),
            getMetrics().updates[cmd->getLoadType()]))
    {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(getUniqueTimestamp());
    }
    auto handle = _mutationSequencer.try_acquire(cmd->getDocumentId());
    if (allowMutation(handle)) {
        _op = std::make_shared<TwoPhaseUpdateOperation>(*this, cmd, getMetrics(), std::move(handle));
    } else {
        sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId()));
    }

    return true;
}


IMPL_MSG_COMMAND_H(ExternalOperationHandler, Remove)
{
    if (!checkTimestampMutationPreconditions(
            *cmd, getBucketId(cmd->getDocumentId()),
            getMetrics().removes[cmd->getLoadType()]))
    {
        return true;
    }

    if (cmd->getTimestamp() == 0) {
        cmd->setTimestamp(getUniqueTimestamp());
    }
    auto handle = _mutationSequencer.try_acquire(cmd->getDocumentId());
    if (allowMutation(handle)) {
        _op = std::make_shared<RemoveOperation>(
                *this,
                cmd,
                getMetrics().removes[cmd->getLoadType()],
                std::move(handle));
    } else {
        sendUp(makeConcurrentMutationRejectionReply(*cmd, cmd->getDocumentId()));
    }

    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, RemoveLocation)
{
    document::BucketId bid;
    RemoveLocationOperation::getBucketId(*this, *cmd, bid);

    if (!checkDistribution(*cmd, bid)) {
        LOG(debug,
            "Distributor manager received %s with wrong distribution",
            cmd->toString().c_str());

        getMetrics().removelocations[cmd->getLoadType()].
            failures.wrongdistributor++;
        return true;
    }

    _op = Operation::SP(new RemoveLocationOperation(
                                *this,
                                cmd,
                                getMetrics().removelocations[cmd->getLoadType()]));
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, Get)
{
    if (!checkDistribution(*cmd, getBucketId(cmd->getDocumentId()))) {
        LOG(debug,
            "Distributor manager received get for %s, "
            "bucket %s with wrong distribution",
            cmd->getDocumentId().toString().c_str(),
            getBucketId(cmd->getDocumentId()).toString().c_str());

        getMetrics().gets[cmd->getLoadType()].failures.wrongdistributor++;
        return true;
    }

    _op = Operation::SP(new GetOperation(
                                *this,
                                cmd,
                                getMetrics().gets[cmd->getLoadType()]));
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, MultiOperation)
{
    if (!checkDistribution(*cmd, cmd->getBucketId())) {
        LOG(debug,
            "Distributor manager received multi-operation message, "
            "bucket %s with wrong distribution",
            cmd->getBucketId().toString().c_str());
        return true;
    }

    _op = Operation::SP(new MultiOperationOperation(
                                *this,
                                cmd,
                                getMetrics().multioperations[cmd->getLoadType()]));
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, StatBucket)
{
    if (!checkDistribution(*cmd, cmd->getBucketId())) {
        return true;
    }

    _op = Operation::SP(new StatBucketOperation(*this, cmd));
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, GetBucketList)
{
    if (!checkDistribution(*cmd, cmd->getBucketId())) {
        return true;
    }
    _op = Operation::SP(new StatBucketListOperation(
            getBucketDatabase(), _operationGenerator, getIndex(), cmd));
    return true;
}

IMPL_MSG_COMMAND_H(ExternalOperationHandler, CreateVisitor)
{
    const DistributorConfiguration& config(getDistributor().getConfig());
    VisitorOperation::Config visitorConfig(
            framework::MilliSecTime(config.getMinTimeLeftToResend()),
            config.getMinBucketsPerVisitor(),
            config.getMaxVisitorsPerNodePerClientVisitor());
    _op = Operation::SP(new VisitorOperation(
                                *this,
                                cmd,
                                visitorConfig,
                                getMetrics().visits[cmd->getLoadType()]));
    return true;
}

} // distributor
} // storage

