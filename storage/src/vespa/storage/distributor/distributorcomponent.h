// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributor_node_context.h"
#include "distributor_operation_context.h"
#include "distributorinterface.h"
#include "document_selection_parser.h"
#include "operationowner.h"
#include "statechecker.h"
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/storageutil/utils.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage::distributor {

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
class DistributorComponent : public storage::DistributorComponent,
                             public DistributorNodeContext,
                             public DistributorOperationContext,
                             public DocumentSelectionParser
{
public:
    DistributorComponent(DistributorInterface& distributor,
                         DistributorBucketSpaceRepo& bucketSpaceRepo,
                         DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
                         DistributorComponentRegister& compReg,
                         const std::string& name);

    ~DistributorComponent() override;

    /**
     * Returns a reference to the current cluster state bundle. Valid until the
     * next time the distributor main thread processes its message queue.
     */
    const lib::ClusterStateBundle& getClusterStateBundle() const;

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

    DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo() { return _readOnlyBucketSpaceRepo; }
    const DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo() const { return _readOnlyBucketSpaceRepo; }

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

    // Implements DistributorNodeContext
    const framework::Clock& clock() const noexcept override { return getClock(); }
    const vespalib::string * cluster_name_ptr() const noexcept override { return cluster_context().cluster_name_ptr(); }
    const document::BucketIdFactory& bucket_id_factory() const noexcept override { return getBucketIdFactory(); }
    uint16_t node_index() const noexcept override { return getIndex(); }

    // Implements DistributorOperationContext
    api::Timestamp generate_unique_timestamp() override { return getUniqueTimestamp(); }
    void update_bucket_database(const document::Bucket& bucket,
                                const BucketCopy& changed_node,
                                uint32_t update_flags = 0) override {
        updateBucketDatabase(bucket, changed_node, update_flags);
    }
    virtual void update_bucket_database(const document::Bucket& bucket,
                                        const std::vector<BucketCopy>& changed_nodes,
                                        uint32_t update_flags = 0) override {
        updateBucketDatabase(bucket, changed_nodes, update_flags);
    }
    void remove_node_from_bucket_database(const document::Bucket& bucket, uint16_t node_index) override {
        removeNodeFromDB(bucket, node_index);
    }
    const DistributorBucketSpaceRepo& bucket_space_repo() const noexcept override {
        return getBucketSpaceRepo();
    }
    DistributorBucketSpaceRepo& bucket_space_repo() noexcept override {
        return getBucketSpaceRepo();
    }
    const DistributorBucketSpaceRepo& read_only_bucket_space_repo() const noexcept override {
        return getReadOnlyBucketSpaceRepo();
    }
    DistributorBucketSpaceRepo& read_only_bucket_space_repo() noexcept override {
        return getReadOnlyBucketSpaceRepo();
    }
    document::BucketId make_split_bit_constrained_bucket_id(const document::DocumentId& docId) const override {
        return getBucketId(docId);
    }
    const DistributorConfiguration& distributor_config() const noexcept override {
        return getDistributor().getConfig();
    }
    void send_inline_split_if_bucket_too_large(document::BucketSpace bucket_space,
                                               const BucketDatabase::Entry& entry,
                                               uint8_t pri) override {
        getDistributor().checkBucketForSplit(bucket_space, entry, pri);
    }
    OperationRoutingSnapshot read_snapshot_for_bucket(const document::Bucket& bucket) const override {
        return getDistributor().read_snapshot_for_bucket(bucket);
    }
    PendingMessageTracker& pending_message_tracker() noexcept override {
        return getDistributor().getPendingMessageTracker();
    }
    bool has_pending_message(uint16_t node_index,
                             const document::Bucket& bucket,
                             uint32_t message_type) const override;
    const lib::ClusterState* pending_cluster_state_or_null(const document::BucketSpace& bucket_space) const override {
        return getDistributor().pendingClusterStateOrNull(bucket_space);
    }
    const lib::ClusterStateBundle& cluster_state_bundle() const override {
        return getClusterStateBundle();
    }
    const char* storage_node_up_states() const override {
        return getDistributor().getStorageNodeUpStates();
    }

    // Implements DocumentSelectionParser
    std::unique_ptr<document::select::Node> parse_selection(const vespalib::string& selection) const override;


private:
    void enumerateUnavailableNodes(
            std::vector<uint16_t>& unavailableNodes,
            const lib::ClusterState& s,
            const document::Bucket& bucket,
            const std::vector<BucketCopy>& candidates) const;
    DistributorInterface& _distributor;

protected:

    DistributorBucketSpaceRepo& _bucketSpaceRepo;
    DistributorBucketSpaceRepo& _readOnlyBucketSpaceRepo;
};

}
