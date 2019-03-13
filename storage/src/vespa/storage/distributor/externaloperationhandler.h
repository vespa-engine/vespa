// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "operation_sequencer.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/distributor/distributorcomponent.h>
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <chrono>

namespace storage {

class DistributorMetricSet;
class PersistenceOperationMetricSet;

namespace distributor {

class Distributor;
class MaintenanceOperationGenerator;

class ExternalOperationHandler : public DistributorComponent,
                                 public api::MessageHandler
{
public:
    using Clock = std::chrono::system_clock;
    using TimePoint = std::chrono::time_point<Clock>;

    DEF_MSG_COMMAND_H(Get);
    DEF_MSG_COMMAND_H(Put);
    DEF_MSG_COMMAND_H(Update);
    DEF_MSG_COMMAND_H(Remove);
    DEF_MSG_COMMAND_H(RemoveLocation);
    DEF_MSG_COMMAND_H(StatBucket);
    DEF_MSG_COMMAND_H(CreateVisitor);
    DEF_MSG_COMMAND_H(GetBucketList);

    ExternalOperationHandler(Distributor& owner,
                             DistributorBucketSpaceRepo& bucketSpaceRepo,
                             DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
                             const MaintenanceOperationGenerator&,
                             DistributorComponentRegister& compReg);

    ~ExternalOperationHandler() override;

    bool handleMessage(const std::shared_ptr<api::StorageMessage>& msg,
                       Operation::SP& operation);

    void rejectFeedBeforeTimeReached(TimePoint timePoint) noexcept {
        _rejectFeedBeforeTimeReached = timePoint;
    }

private:
    const MaintenanceOperationGenerator& _operationGenerator;
    OperationSequencer _mutationSequencer;
    Operation::SP _op;
    TimePoint _rejectFeedBeforeTimeReached;

    template <typename Func>
    void bounce_or_invoke_read_only_op(api::StorageCommand& cmd,
                                       const document::Bucket& bucket,
                                       PersistenceOperationMetricSet& metrics,
                                       Func f);

    void bounce_with_wrong_distribution(api::StorageCommand& cmd);
    void bounce_with_busy_during_state_transition(api::StorageCommand& cmd,
                                                  const lib::ClusterState& current_state,
                                                  const lib::ClusterState& pending_state);

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

    DistributorMetricSet& getMetrics() { return getDistributor().getMetrics(); }
};

}

}

