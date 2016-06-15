// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/bucketdb/distrbucketdb.h>
#include <vespa/storage/distributor/distributorcomponent.h>
#include <vespa/storage/distributor/visitormetricsset.h>
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <vespa/storageframework/storageframework.h>

namespace storage {

class DistributorMetricSet;

namespace distributor {

class Distributor;
class MaintenanceOperationGenerator;

class ExternalOperationHandler : public DistributorComponent,
                                 public api::MessageHandler
{
public:
    DEF_MSG_COMMAND_H(Get);
    DEF_MSG_COMMAND_H(Put);
    DEF_MSG_COMMAND_H(Update);
    DEF_MSG_COMMAND_H(Remove);
    DEF_MSG_COMMAND_H(RemoveLocation);
    DEF_MSG_COMMAND_H(MultiOperation);
    DEF_MSG_COMMAND_H(StatBucket);
    DEF_MSG_COMMAND_H(CreateVisitor);
    DEF_MSG_COMMAND_H(GetBucketList);

    ExternalOperationHandler(Distributor& owner,
                             const MaintenanceOperationGenerator&,
                             DistributorComponentRegister& compReg);

    ~ExternalOperationHandler();

    bool handleMessage(const std::shared_ptr<api::StorageMessage>& msg,
                       Operation::SP& operation);

private:
    metrics::LoadMetric<VisitorMetricSet> _visitorMetrics;
    const MaintenanceOperationGenerator& _operationGenerator;
    Operation::SP _op;

    DistributorMetricSet& getMetrics() { return getDistributor().getMetrics(); }
};

}

}

