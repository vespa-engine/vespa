// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributor_node_context.h"
#include "distributor_stripe_interface.h"
#include "distributor_stripe_operation_context.h"
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
 * TODO STRIPE update class comment.
 */
class DistributorStripeComponent : public storage::DistributorComponent,
                                   public DistributorNodeContext,
                                   public DistributorStripeOperationContext,
                                   public DocumentSelectionParser
{
public:
    DistributorStripeComponent(DistributorStripeInterface& distributor,
                               DistributorBucketSpaceRepo& bucketSpaceRepo,
                               DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
                               DistributorComponentRegister& compReg,
                               const std::string& name);

    ~DistributorStripeComponent() override;

    void sendDown(const api::StorageMessage::SP&);
    void sendUp(const api::StorageMessage::SP&);

    DistributorStripeInterface& getDistributor() { return _distributor; }

    const DistributorStripeInterface& getDistributor() const {
        return _distributor;
    }

    // Implements DistributorNodeContext
    const framework::Clock& clock() const noexcept override { return getClock(); }
    const vespalib::string * cluster_name_ptr() const noexcept override { return cluster_context().cluster_name_ptr(); }
    const document::BucketIdFactory& bucket_id_factory() const noexcept override { return getBucketIdFactory(); }
    uint16_t node_index() const noexcept override { return getIndex(); }

    /**
     * Returns the slobrok address of the given storage node.
     */
    api::StorageMessageAddress node_address(uint16_t node_index) const noexcept override;

    // Implements DistributorStripeOperationContext
    api::Timestamp generate_unique_timestamp() override { return getUniqueTimestamp(); }

    /**
     * Simple API for the common case of modifying a single node.
     */
    void update_bucket_database(const document::Bucket& bucket,
                                const BucketCopy& changed_node,
                                uint32_t update_flags) override {
        update_bucket_database(bucket,
                               toVector<BucketCopy>(changed_node),
                               update_flags);
    }

    /**
     * Adds the given copies to the bucket database.
     */
    void update_bucket_database(const document::Bucket& bucket,
                                const std::vector<BucketCopy>& changed_nodes,
                                uint32_t update_flags) override;

    /**
     * Removes a copy from the given bucket from the bucket database.
     * If the resulting bucket is empty afterwards, removes the entire
     * bucket entry from the bucket database.
     */
    void remove_node_from_bucket_database(const document::Bucket& bucket, uint16_t node_index) override {
        remove_nodes_from_bucket_database(bucket, toVector<uint16_t>(node_index));
    }

    /**
     * Removes the given bucket copies from the bucket database.
     * If the resulting bucket is empty afterwards, removes the entire
     * bucket entry from the bucket database.
     */
    void remove_nodes_from_bucket_database(const document::Bucket& bucket,
                                           const std::vector<uint16_t>& nodes) override;

    const DistributorBucketSpaceRepo& bucket_space_repo() const noexcept override {
        return _bucketSpaceRepo;
    }
    DistributorBucketSpaceRepo& bucket_space_repo() noexcept override {
        return _bucketSpaceRepo;
    }
    const DistributorBucketSpaceRepo& read_only_bucket_space_repo() const noexcept override {
        return _readOnlyBucketSpaceRepo;
    }
    DistributorBucketSpaceRepo& read_only_bucket_space_repo() noexcept override {
        return _readOnlyBucketSpaceRepo;
    }
    document::BucketId make_split_bit_constrained_bucket_id(const document::DocumentId& doc_id) const override;

    /**
     * Fetch bucket info about the given bucket from the given node.
     * Used when we get BUCKET_NOT_FOUND.
     */
    void recheck_bucket_info(uint16_t node_index, const document::Bucket& bucket) override;

    /**
     * Finds a bucket that has the same direct parent as the given bucket
     * (i.e. split one bit less), but different bit in the most used bit.
     */
    document::BucketId get_sibling(const document::BucketId& bid) const override;

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
    const PendingMessageTracker& pending_message_tracker() const noexcept override {
        return getDistributor().getPendingMessageTracker();
    }
    bool has_pending_message(uint16_t node_index,
                             const document::Bucket& bucket,
                             uint32_t message_type) const override;
    const lib::ClusterState* pending_cluster_state_or_null(const document::BucketSpace& bucket_space) const override {
        return getDistributor().pendingClusterStateOrNull(bucket_space);
    }

    /**
     * Returns a reference to the current cluster state bundle. Valid until the
     * next time the distributor main thread processes its message queue.
     */
    const lib::ClusterStateBundle& cluster_state_bundle() const override;

    /**
     * Returns true if the given storage node is in an "up state".
     */
    bool storage_node_is_up(document::BucketSpace bucket_space, uint32_t node_index) const override;

    const BucketGcTimeCalculator::BucketIdHasher& bucket_id_hasher() const override {
        return getDistributor().getBucketIdHasher();
    }

    const NodeSupportedFeaturesRepo& node_supported_features_repo() const noexcept override;

    // Implements DocumentSelectionParser
    std::unique_ptr<document::select::Node> parse_selection(const vespalib::string& selection) const override;

private:
    void enumerateUnavailableNodes(
            std::vector<uint16_t>& unavailableNodes,
            const lib::ClusterState& s,
            const document::Bucket& bucket,
            const std::vector<BucketCopy>& candidates) const;
    DistributorStripeInterface& _distributor;

protected:

    DistributorBucketSpaceRepo& _bucketSpaceRepo;
    DistributorBucketSpaceRepo& _readOnlyBucketSpaceRepo;
};

}
