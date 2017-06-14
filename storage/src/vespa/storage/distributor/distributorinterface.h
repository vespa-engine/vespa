// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/distributor/maintenancebucket.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/distributor/bucketgctimecalculator.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/config/distributorconfiguration.h>
#include <vespa/storage/distributor/distributormessagesender.h>
#include <vespa/storage/distributor/bucketownership.h>

namespace storage {

namespace distributor {

class DistributorInterface : public DistributorMessageSender
{
public:
    virtual PendingMessageTracker& getPendingMessageTracker() = 0;
    virtual const lib::Distribution& getDistribution() const = 0;

    virtual DistributorMetricSet& getMetrics() = 0;

    virtual void enableClusterState(const lib::ClusterState& state) = 0;

    virtual BucketOwnership checkOwnershipInPendingState(const document::BucketId&) const = 0;

    virtual void notifyDistributionChangeEnabled() = 0;

    /**
     * Requests that we send a requestBucketInfo for the given bucket to the given
     * node. Should be called whenever we receive a BUCKET_NOT_FOUND result.
     */
    virtual void recheckBucketInfo(uint16_t nodeIdx, const document::BucketId& bid) = 0;

    virtual bool handleReply(const std::shared_ptr<api::StorageReply>& reply) = 0;

    /**
     * Checks whether a bucket needs to be split, and sends a split
     * if so.
     *
     * @param e The bucket to check.
     * @param pri The priority the split should be sent at.
     */
    virtual void checkBucketForSplit(const BucketDatabase::Entry& e, uint8_t pri) = 0;

    /**
     * @return Returns the current cluster state.
     */
    virtual const lib::ClusterState& getClusterState() const = 0;

    /**
     * Returns true if the node is currently initializing.
     */
    virtual bool initializing() const = 0;

    virtual void handleCompletedMerge(const std::shared_ptr<api::MergeBucketReply>&) = 0;

    virtual const char* getStorageNodeUpStates() const = 0;
    
    virtual const DistributorConfiguration& getConfig() const = 0;

    virtual ChainedMessageSender& getMessageSender() = 0;

    virtual const BucketGcTimeCalculator::BucketIdHasher& getBucketIdHasher() const = 0;
};

}

}

