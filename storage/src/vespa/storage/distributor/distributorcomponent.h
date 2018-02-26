// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributorinterface.h"
#include "operationowner.h"
#include "statechecker.h"
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/storageutil/utils.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage {

namespace distributor {

class DistributorBucketSpaceRepo;

struct DatabaseUpdate {
    enum UpdateFlags {
        CREATE_IF_NONEXISTING = 1,
        RESET_TRUSTED = 2
    };  
};

/**
 * Takes care of subscribing to document manager config and
 * making those values available to other subcomponents.
 */
class DistributorComponent : public storage::DistributorComponent
{
public:
    DistributorComponent(DistributorInterface& distributor,
                         DistributorBucketSpaceRepo &bucketSpaceRepo,
  		         DistributorComponentRegister& compReg,
		         const std::string& name);

    ~DistributorComponent();

    /**
     * Returns the ownership status of a bucket as decided with the given
     * distribution and cluster state -and- that of the pending cluster
     * state and distribution (if any pending exists).
     */
    BucketOwnership checkOwnershipInPendingAndGivenState(
            const lib::Distribution& distribution,
            const lib::ClusterState& clusterState,
            const document::Bucket &bucket) const;

    BucketOwnership checkOwnershipInPendingAndCurrentState(
            const document::Bucket &bucket) const;

    bool ownsBucketInState(const lib::Distribution& distribution,
                           const lib::ClusterState& clusterState,
                           const document::Bucket &bucket) const;

    /**
     * Returns true if this distributor owns the given bucket in the
     * given cluster and current distribution config.
     */
    bool ownsBucketInState(const lib::ClusterState& clusterState,
                           const document::Bucket &bucket) const;

    /**
     * Returns true if this distributor owns the given bucket with the current
     * cluster state and distribution config.
     */
    bool ownsBucketInCurrentState(const document::Bucket &bucket) const;

    /**
     * Returns a reference to the current cluster state bundle. Valid until the
     * next time the distributor main thread processes its message queue.
     */
    const lib::ClusterStateBundle& getClusterStateBundle() const;

    /**
     * Returns the ideal nodes for the given bucket.
     */
    std::vector<uint16_t> getIdealNodes(const document::Bucket &bucket) const;

    /**
      * Returns the slobrok address of the given storage node.
      */
    api::StorageMessageAddress nodeAddress(uint16_t nodeIndex) const;

    /**
     * Returns true if the given storage node is in an "up state".
     */
    bool storageNodeIsUp(document::BucketSpace bucketSpace, uint32_t nodeIndex) const;

    /**
     * Verifies that the given command has been received at the
     * correct distributor based on the current system state.
     */
    bool checkDistribution(api::StorageCommand& cmd, const document::Bucket &bucket);

    /**
     * Removes the given bucket copies from the bucket database.
     * If the resulting bucket is empty afterwards, removes the entire
     * bucket entry from the bucket database.
     */
    void removeNodesFromDB(const document::Bucket &bucket,
                           const std::vector<uint16_t>& nodes);

    /**
     * Removes a copy from the given bucket from the bucket database.
     * If the resulting bucket is empty afterwards, removes the entire
     * bucket entry from the bucket database.
     */
    void removeNodeFromDB(const document::Bucket &bucket, uint16_t node) {
        removeNodesFromDB(bucket, toVector<uint16_t>(node));
    }

    /**
     * Adds the given copies to the bucket database.
     */
    void updateBucketDatabase(
            const document::Bucket &bucket,
            const std::vector<BucketCopy>& changedNodes,
            uint32_t updateFlags = 0);

    /**
     * Simple API for the common case of modifying a single node.
     */
    void updateBucketDatabase(
            const document::Bucket &bucket,
            const BucketCopy& changedNode,
            uint32_t updateFlags = 0)
    {
        updateBucketDatabase(bucket,
                             toVector<BucketCopy>(changedNode),
                             updateFlags);
    }

    /**
     * Fetch bucket info about the given bucket from the given node.
     * Used when we get BUCKET_NOT_FOUND.
     */
    void recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket);

    /**
     * Returns the bucket id corresponding to the given document id.
     */
    document::BucketId getBucketId(const document::DocumentId& docId) const;

    void sendDown(const api::StorageMessage::SP&);
    void sendUp(const api::StorageMessage::SP&);

    DistributorInterface& getDistributor() { return _distributor; }

    const DistributorInterface& getDistributor() const {
        return _distributor;
    }

    DistributorBucketSpaceRepo &getBucketSpaceRepo() { return _bucketSpaceRepo; }
    const DistributorBucketSpaceRepo &getBucketSpaceRepo() const { return _bucketSpaceRepo; }

    /**
     * Finds a bucket that has the same direct parent as the given bucket
     * (i.e. split one bit less), but different bit in the most used bit.
     */
    document::BucketId getSibling(const document::BucketId& bid) const;

    /**
     * Create a bucket that is split correctly according to other buckets that
     * are in the bucket database.
     */
    BucketDatabase::Entry createAppropriateBucket(const document::Bucket &bucket);

    /**
     * Returns true if the node is currently initializing.
     */
    bool initializing() const;

private:
    std::vector<uint16_t> enumerateDownNodes(
            const lib::ClusterState& s,
            const document::Bucket &bucket,
            const std::vector<BucketCopy>& candidates) const;
    DistributorInterface& _distributor;

protected:

    DistributorBucketSpaceRepo &_bucketSpaceRepo;
    vespalib::Lock _sync;
};

}

}

