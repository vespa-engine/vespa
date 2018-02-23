// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributorcomponent.h"
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/vdslib/distribution/distribution.h>
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"

#include <vespa/log/log.h>
LOG_SETUP(".distributorstoragelink");

using document::BucketSpace;

namespace storage {

namespace distributor {

DistributorComponent::DistributorComponent(
        DistributorInterface& distributor,
        DistributorBucketSpaceRepo &bucketSpaceRepo,
        DistributorComponentRegister& compReg,
        const std::string& name)
    : storage::DistributorComponent(compReg, name),
      _distributor(distributor),
      _bucketSpaceRepo(bucketSpaceRepo)
{
}

DistributorComponent::~DistributorComponent() {}

void
DistributorComponent::sendDown(const api::StorageMessage::SP& msg)
{
    _distributor.getMessageSender().sendDown(msg);
}

void
DistributorComponent::sendUp(const api::StorageMessage::SP& msg)
{
    _distributor.getMessageSender().sendUp(msg);
}

const lib::ClusterState&
DistributorComponent::getClusterState() const
{
    return _distributor.getClusterState();
};

std::vector<uint16_t>
DistributorComponent::getIdealNodes(const document::Bucket &bucket) const
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    return bucketSpace.getDistribution().getIdealStorageNodes(
            bucketSpace.getClusterState(),
            bucket.getBucketId(),
            _distributor.getStorageNodeUpStates());
}

BucketOwnership
DistributorComponent::checkOwnershipInPendingAndGivenState(
        const lib::Distribution& distribution,
        const lib::ClusterState& clusterState,
        const document::Bucket &bucket) const
{
    try {
        BucketOwnership pendingRes(
                _distributor.checkOwnershipInPendingState(bucket));
        if (!pendingRes.isOwned()) {
            return pendingRes;
        }
        uint16_t distributor = distribution.getIdealDistributorNode(
                clusterState, bucket.getBucketId());

        if (getIndex() == distributor) {
            return BucketOwnership::createOwned();
        } else {
            return BucketOwnership::createNotOwnedInState(clusterState);
        }
    } catch (lib::TooFewBucketBitsInUseException& e) {
        return BucketOwnership::createNotOwnedInState(clusterState);
    } catch (lib::NoDistributorsAvailableException& e) {
        return BucketOwnership::createNotOwnedInState(clusterState);
    }
}

BucketOwnership
DistributorComponent::checkOwnershipInPendingAndCurrentState(
        const document::Bucket &bucket) const
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    return checkOwnershipInPendingAndGivenState(
            bucketSpace.getDistribution(), bucketSpace.getClusterState(), bucket);
}

bool
DistributorComponent::ownsBucketInState(
        const lib::Distribution& distribution,
        const lib::ClusterState& clusterState,
        const document::Bucket &bucket) const
{
    LOG(spam, "checking bucket %s in state %s with distr %s",
        bucket.toString().c_str(), clusterState.toString().c_str(),
        distribution.getNodeGraph().getDistributionConfigHash().c_str());
    try {
        uint16_t distributor = distribution.getIdealDistributorNode(
                clusterState, bucket.getBucketId());

        return (getIndex() == distributor);
    } catch (lib::TooFewBucketBitsInUseException& e) {
        return false;
    } catch (lib::NoDistributorsAvailableException& e) {
        return false;
    }
}

bool
DistributorComponent::ownsBucketInState(
        const lib::ClusterState& clusterState,
        const document::Bucket &bucket) const
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    return ownsBucketInState(bucketSpace.getDistribution(), clusterState, bucket);
}

bool
DistributorComponent::ownsBucketInCurrentState(const document::Bucket &bucket) const
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    return ownsBucketInState(bucketSpace.getDistribution(), bucketSpace.getClusterState(), bucket);
}

api::StorageMessageAddress
DistributorComponent::nodeAddress(uint16_t nodeIndex) const
{
    return api::StorageMessageAddress(
            getClusterName(),
            lib::NodeType::STORAGE,
            nodeIndex);
}

bool
DistributorComponent::checkDistribution(
        api::StorageCommand &cmd,
        const document::Bucket &bucket)
{
    BucketOwnership bo(checkOwnershipInPendingAndCurrentState(bucket));
    if (!bo.isOwned()) {
        std::string systemStateStr = bo.getNonOwnedState().toString();
        LOG(debug,
            "Got message with wrong distribution, "
            "bucket %s sending back state '%s'",
            bucket.toString().c_str(),
            systemStateStr.c_str());

        api::StorageReply::UP reply(cmd.makeReply());
        api::ReturnCode ret(
                api::ReturnCode::WRONG_DISTRIBUTION,
                systemStateStr);
        reply->setResult(ret);
        sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
        return false;
    }
    return true;
}

void
DistributorComponent::removeNodesFromDB(const document::Bucket &bucket,
                                          const std::vector<uint16_t>& nodes)
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    BucketDatabase::Entry dbentry = bucketSpace.getBucketDatabase().get(bucket.getBucketId());

    if (dbentry.valid()) {
        for (uint32_t i = 0; i < nodes.size(); ++i) {
            if (dbentry->removeNode(nodes[i])) {
                LOG(debug,
                "Removed node %d from bucket %s. %u copies remaining",
                    nodes[i],
                    bucket.toString().c_str(),
                    dbentry->getNodeCount());
            }
        }

        if (dbentry->getNodeCount() != 0) {
            bucketSpace.getBucketDatabase().update(dbentry);
        } else {
            LOG(debug,
                "After update, bucket %s now has no copies. "
                "Removing from database.",
                bucket.toString().c_str());

            bucketSpace.getBucketDatabase().remove(bucket.getBucketId());
        }
    }
}

std::vector<uint16_t>
DistributorComponent::enumerateDownNodes(
        const lib::ClusterState& s,
        const document::Bucket &bucket,
        const std::vector<BucketCopy>& candidates) const
{
    std::vector<uint16_t> downNodes;
    for (uint32_t i = 0; i < candidates.size(); ++i) {
        const BucketCopy& copy(candidates[i]);
        const lib::NodeState& ns(
                s.getNodeState(lib::Node(lib::NodeType::STORAGE,
                                         copy.getNode())));
        if (ns.getState() == lib::State::DOWN) {
            LOG(debug,
                "Trying to add a bucket copy to %s whose node is marked as "
                "down in the cluster state: %s. Ignoring it since no zombies "
                "are allowed!",
                bucket.toString().c_str(),
                copy.toString().c_str());
            downNodes.push_back(copy.getNode());
        }
    }
    return downNodes;
}

void
DistributorComponent::updateBucketDatabase(
        const document::Bucket &bucket,
        const std::vector<BucketCopy>& changedNodes,
        uint32_t updateFlags)
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    assert(!(bucket.getBucketId() == document::BucketId()));
    BucketDatabase::Entry dbentry = bucketSpace.getBucketDatabase().get(bucket.getBucketId());

    BucketOwnership ownership(checkOwnershipInPendingAndCurrentState(bucket));
    if (!ownership.isOwned()) {
        LOG(debug,
            "Trying to add %s to database that we do not own according to "
            "cluster state '%s' - ignoring!",
            bucket.toString().c_str(),
            ownership.getNonOwnedState().toString().c_str());
        LOG_BUCKET_OPERATION_NO_LOCK(bucketId, "Ignoring database insert since "
                                     "we do not own the bucket");
        return;
    }

    if (!dbentry.valid()) {
        if (updateFlags & DatabaseUpdate::CREATE_IF_NONEXISTING) {
            dbentry = BucketDatabase::Entry(bucket.getBucketId(), BucketInfo());
        } else {
            return;
        }
    }

    // 0 implies bucket was just added. Since we don't know if any other
    // distributor has run GC on it, we just have to assume this and set the
    // timestamp to the current time to avoid duplicate work.
    if (dbentry->getLastGarbageCollectionTime() == 0) {
        dbentry->setLastGarbageCollectionTime(
                getClock().getTimeInSeconds().getTime());
    }

    // Ensure that we're not trying to bring any zombie copies into the
    // bucket database (i.e. copies on nodes that are actually down).
    std::vector<uint16_t> downNodes(
            enumerateDownNodes(bucketSpace.getClusterState(), bucket, changedNodes));
    // Optimize for common case where we don't have to create a new
    // bucket copy vector
    if (downNodes.empty()) {
        dbentry->addNodes(changedNodes, getIdealNodes(bucket));
    } else {
        std::vector<BucketCopy> upNodes;
        for (uint32_t i = 0; i < changedNodes.size(); ++i) {
            const BucketCopy& copy(changedNodes[i]);
            if (std::find(downNodes.begin(), downNodes.end(),
                          copy.getNode())
                == downNodes.end())
            {
                upNodes.push_back(copy);
            }
        }
        dbentry->addNodes(upNodes, getIdealNodes(bucket));
    }
    if (updateFlags & DatabaseUpdate::RESET_TRUSTED) {
        dbentry->resetTrusted();
    }
    if (dbentry->getNodeCount() == 0) {
        LOG(warning, "all nodes in changedNodes set (size %zu) are down, removing dbentry", changedNodes.size());
        bucketSpace.getBucketDatabase().remove(bucket.getBucketId());
        return;
    }
    bucketSpace.getBucketDatabase().update(dbentry);
}

void
DistributorComponent::recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket)
{
    _distributor.recheckBucketInfo(nodeIdx, bucket);
}

document::BucketId
DistributorComponent::getBucketId(const document::DocumentId& docId) const
{
    document::BucketId id(getBucketIdFactory().getBucketId(docId));

    id.setUsedBits(_distributor.getConfig().getMinimalBucketSplit());
    return id.stripUnused();
}

bool
DistributorComponent::storageNodeIsUp(uint32_t nodeIndex) const
{
    const lib::NodeState& ns = getClusterState().getNodeState(
            lib::Node(lib::NodeType::STORAGE, nodeIndex));

    return ns.getState().oneOf(_distributor.getStorageNodeUpStates());
}

document::BucketId
DistributorComponent::getSibling(const document::BucketId& bid) const {
    document::BucketId zeroBucket;
    document::BucketId oneBucket;

    if (bid.getUsedBits() == 1) {
        zeroBucket = document::BucketId(1, 0);
        oneBucket = document::BucketId(1, 1);
    } else {
        document::BucketId joinedBucket = document::BucketId(
                bid.getUsedBits() - 1,
                bid.getId());

        zeroBucket = document::BucketId(
                bid.getUsedBits(),
                joinedBucket.getId());

        uint64_t hiBit = 1;
        hiBit <<= (bid.getUsedBits() - 1);
        oneBucket = document::BucketId(
                bid.getUsedBits(),
                joinedBucket.getId() | hiBit);
    }

    return (zeroBucket == bid) ? oneBucket : zeroBucket;
};

BucketDatabase::Entry
DistributorComponent::createAppropriateBucket(const document::Bucket &bucket)
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    return bucketSpace.getBucketDatabase().createAppropriateBucket(
            _distributor.getConfig().getMinimalBucketSplit(),
            bucket.getBucketId());
}

bool
DistributorComponent::initializing() const {
    return _distributor.initializing();
}

}

}
