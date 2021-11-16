// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketgctimecalculator.h"
#include "bucketownership.h"
#include "operation_routing_snapshot.h"
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storageapi/defs.h>

namespace document { class Bucket; }
namespace storage::lib { class ClusterStateBundle; }

namespace storage::distributor {

class PendingMessageTracker;
class NodeSupportedFeaturesRepo;

/**
 * Interface with functionality that is used when handling distributor stripe operations.
 */
class DistributorStripeOperationContext {
public:
    virtual ~DistributorStripeOperationContext() = default;

    virtual api::Timestamp generate_unique_timestamp() = 0;
    virtual const DistributorBucketSpaceRepo& bucket_space_repo() const noexcept = 0;
    virtual DistributorBucketSpaceRepo& bucket_space_repo() noexcept = 0;
    virtual const DistributorBucketSpaceRepo& read_only_bucket_space_repo() const noexcept = 0;
    virtual DistributorBucketSpaceRepo& read_only_bucket_space_repo() noexcept = 0;
    virtual const DistributorConfiguration& distributor_config() const noexcept = 0;

    virtual void update_bucket_database(const document::Bucket& bucket,
                                        const BucketCopy& changed_node,
                                        uint32_t update_flags = 0) = 0;
    virtual void update_bucket_database(const document::Bucket& bucket,
                                        const std::vector<BucketCopy>& changed_nodes,
                                        uint32_t update_flags = 0) = 0;
    virtual void remove_node_from_bucket_database(const document::Bucket& bucket, uint16_t node_index) = 0;
    virtual void remove_nodes_from_bucket_database(const document::Bucket& bucket,
                                                   const std::vector<uint16_t>& nodes) = 0;
    virtual document::BucketId make_split_bit_constrained_bucket_id(const document::DocumentId& docId) const = 0;
    virtual void recheck_bucket_info(uint16_t node_index, const document::Bucket& bucket) = 0;
    virtual document::BucketId get_sibling(const document::BucketId& bid) const = 0;

    virtual void send_inline_split_if_bucket_too_large(document::BucketSpace bucket_space,
                                                       const BucketDatabase::Entry& entry,
                                                       uint8_t pri) = 0;
    virtual OperationRoutingSnapshot read_snapshot_for_bucket(const document::Bucket& bucket) const = 0;
    virtual PendingMessageTracker& pending_message_tracker() noexcept = 0;
    virtual const PendingMessageTracker& pending_message_tracker() const noexcept = 0;
    virtual bool has_pending_message(uint16_t node_index,
                                     const document::Bucket& bucket,
                                     uint32_t message_type) const = 0;
    virtual const lib::ClusterState* pending_cluster_state_or_null(const document::BucketSpace& bucket_space) const = 0;
    virtual const lib::ClusterStateBundle& cluster_state_bundle() const = 0;
    virtual bool storage_node_is_up(document::BucketSpace bucket_space, uint32_t node_index) const = 0;
    virtual const BucketGcTimeCalculator::BucketIdHasher& bucket_id_hasher() const = 0;
    virtual const NodeSupportedFeaturesRepo& node_supported_features_repo() const noexcept = 0;
};

}
