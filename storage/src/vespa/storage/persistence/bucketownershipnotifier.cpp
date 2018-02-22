// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketownershipnotifier.h"
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/backtrace.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.bucketownershipnotifier");

using document::BucketSpace;

namespace storage {

uint16_t
BucketOwnershipNotifier::getOwnerDistributorForBucket(
        const document::Bucket &bucket) const
{
    try {
        auto distribution(_component.getBucketSpaceRepo().get(bucket.getBucketSpace()).getDistribution());
        const auto clusterStateBundle = _component.getStateUpdater().getClusterStateBundle();
        const auto &clusterState = *clusterStateBundle->getDerivedClusterState(bucket.getBucketSpace());
        return (distribution->getIdealDistributorNode(clusterState, bucket.getBucketId()));
        // If we get exceptions there aren't any distributors, so they'll have
        // to explicitly fetch all bucket info eventually anyway.
    } catch (lib::TooFewBucketBitsInUseException& e) {
        LOGBP(debug, "Too few bucket bits used for %s to be assigned "
              "to a distributor. Not notifying any distributor of "
              "bucket change.",
              bucket.toString().c_str());
    } catch (lib::NoDistributorsAvailableException& e) {
        LOGBP(debug, "No distributors available. Not notifying any "
              "distributor of bucket change.");
    } catch (const std::exception& e) {
        LOG(error,
            "Got unknown exception while resolving distributor: %s",
            e.what());
    }
    return FAILED_TO_RESOLVE;
}

bool
BucketOwnershipNotifier::distributorOwns(uint16_t distributor,
                                         const document::Bucket &bucket) const
{
    return (distributor == getOwnerDistributorForBucket(bucket));
}

void
BucketOwnershipNotifier::sendNotifyBucketToDistributor(
        uint16_t distributorIndex,
        const document::Bucket &bucket,
        const api::BucketInfo& infoToSend)
{
    if (!infoToSend.valid()) {
        LOG(error,
            "Trying to send invalid bucket info to distributor %u: %s. %s",
            distributorIndex,
            infoToSend.toString().c_str(),
            vespalib::getStackTrace(0).c_str());
        return;
    }
    api::NotifyBucketChangeCommand::SP notifyCmd(
                new api::NotifyBucketChangeCommand(bucket, infoToSend));

    notifyCmd->setAddress(api::StorageMessageAddress(
                                  _component.getClusterName(),
                                  lib::NodeType::DISTRIBUTOR,
                                  distributorIndex));
    notifyCmd->setSourceIndex(_component.getIndex());
    LOG(debug,
        "Sending notify to distributor %u: %s",
        distributorIndex,
        notifyCmd->toString().c_str());
    _sender.sendCommand(notifyCmd);
}

void
BucketOwnershipNotifier::logNotification(const document::Bucket &bucket,
                                         uint16_t sourceIndex,
                                         uint16_t currentOwnerIndex,
                                         const api::BucketInfo& newInfo)
{
    LOG(debug,
        "%s now owned by distributor %u, but reply for operation is scheduled "
        "to go to distributor %u. Sending NotifyBucketChange with %s to ensure "
        "new owner knows bucket exists",
        bucket.getBucketId().toString().c_str(),
        currentOwnerIndex,
        sourceIndex,
        newInfo.toString().c_str());
    LOG_BUCKET_OPERATION_NO_LOCK(
            bucket,
            vespalib::make_string(
                    "Sending notify to distributor %u "
                    "(ownership changed away from %u)",
                    currentOwnerIndex, sourceIndex));
}

void
BucketOwnershipNotifier::notifyIfOwnershipChanged(
        const document::Bucket &bucket,
        uint16_t sourceIndex,
        const api::BucketInfo& infoToSend)
{
    uint16_t distributor(getOwnerDistributorForBucket(bucket));

    if (distributor == sourceIndex || distributor == FAILED_TO_RESOLVE) {
        return;
    }
    if (sourceIndex == FAILED_TO_RESOLVE) {
        LOG(debug,
            "Got an invalid source index of %u; impossible to know if "
            "bucket ownership has changed. %s",
            sourceIndex,
            vespalib::getStackTrace(0).c_str());
        return;
    }
    logNotification(bucket, sourceIndex, distributor, infoToSend);
    sendNotifyBucketToDistributor(distributor, bucket, infoToSend);
}

void
BucketOwnershipNotifier::sendNotifyBucketToCurrentOwner(
        const document::Bucket &bucket,
        const api::BucketInfo& infoToSend)
{
    uint16_t distributor(getOwnerDistributorForBucket(bucket));
    if (distributor == FAILED_TO_RESOLVE) {
        return;
    }
    sendNotifyBucketToDistributor(distributor, bucket, infoToSend);
}

NotificationGuard::~NotificationGuard()
{
    for (uint32_t i = 0; i < _bucketsToCheck.size(); ++i) {
        const BucketToCheck& b(_bucketsToCheck[i]);
        if (b.alwaysSend) {
            _notifier.sendNotifyBucketToCurrentOwner(b.bucket, b.info);
        } else {
            _notifier.notifyIfOwnershipChanged(b.bucket, b.sourceIndex, b.info);
        }
    }
}

void
NotificationGuard::notifyIfOwnershipChanged(const document::Bucket &bucket,
                                            uint16_t sourceIndex,
                                            const api::BucketInfo& infoToSend)
{
    _bucketsToCheck.push_back(BucketToCheck(bucket, sourceIndex, infoToSend));
}

void
NotificationGuard::notifyAlways(const document::Bucket &bucket,
                                const api::BucketInfo& infoToSend)
{
    BucketToCheck bc(bucket, 0xffff, infoToSend);
    bc.alwaysSend = true;
    _bucketsToCheck.push_back(bc);
}

} // storage

