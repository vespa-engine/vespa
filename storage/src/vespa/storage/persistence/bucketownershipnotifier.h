// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/document/bucket/bucket.h>
#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/common/servicelayercomponent.h>

namespace storage {

class BucketOwnershipNotifier
{
    const ServiceLayerComponent & _component;
    MessageSender               & _sender;
public:
    BucketOwnershipNotifier(const ServiceLayerComponent& component, MessageSender& sender)
        : _component(component),
          _sender(sender)
    {}

    bool distributorOwns(uint16_t distributor, const document::Bucket &bucket) const;
    void notifyIfOwnershipChanged(const document::Bucket &bucket, uint16_t sourceIndex, const api::BucketInfo& infoToSend);
    void sendNotifyBucketToCurrentOwner(const document::Bucket &bucket, const api::BucketInfo& infoToSend);
private:
    enum IndexMeta {
        FAILED_TO_RESOLVE = 0xffff
    };

    void sendNotifyBucketToDistributor(uint16_t distributorIndex, const document::Bucket &bucket,
                                       const api::BucketInfo& infoToSend);

    // Returns either index or FAILED_TO_RESOLVE
    uint16_t getOwnerDistributorForBucket(const document::Bucket &bucket) const;

    void logNotification(const document::Bucket &bucket, uint16_t sourceIndex,
                         uint16_t currentOwnerIndex, const api::BucketInfo& newInfo);
};

/**
 * Convenience class for sending notifications at the end of a scope, primarily
 * to avoid issues with sending while holding a bucket lock.
 */
class NotificationGuard
{
    struct BucketToCheck
    {
        BucketToCheck(const document::Bucket& _bucket, uint16_t _sourceIndex, const api::BucketInfo& _info)
          : bucket(_bucket),
            info(_info),
            sourceIndex(_sourceIndex),
            alwaysSend(false)
        {}

        document::Bucket bucket;
        api::BucketInfo  info;
        uint16_t         sourceIndex;
        bool             alwaysSend;
    };
    BucketOwnershipNotifier& _notifier;
    std::vector<BucketToCheck> _bucketsToCheck;
public:
    NotificationGuard(BucketOwnershipNotifier& notifier)
        : _notifier(notifier),
          _bucketsToCheck()
    {}
    NotificationGuard(const NotificationGuard&) = delete;
    NotificationGuard& operator=(const NotificationGuard&) = delete;

    ~NotificationGuard();

    void notifyIfOwnershipChanged(const document::Bucket &bucket, uint16_t sourceIndex, const api::BucketInfo& infoToSend);
    void notifyAlways(const document::Bucket &bucket, const api::BucketInfo& infoToSend);
};

} // storage
