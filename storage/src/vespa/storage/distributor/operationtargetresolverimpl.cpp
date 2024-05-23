// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationtargetresolverimpl.h"
#include "distributor_bucket_space.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/printable.hpp>
#include <sstream>
#include <cassert>

namespace storage::distributor {

BucketInstance::BucketInstance(const document::BucketId& id, const api::BucketInfo& info, lib::Node node,
                               uint16_t ideal_location_priority, uint16_t db_entry_order,
                               bool trusted, bool exist) noexcept
    : _bucket(id), _info(info), _node(node),
      _ideal_location_priority(ideal_location_priority),
      _db_entry_order(db_entry_order),
      _trusted(trusted), _exists(exist)
{
}

void
BucketInstance::print(vespalib::asciistream& out, const PrintProperties&) const
{
    std::string infoString(_info.toString());
    infoString = infoString.substr(10, infoString.size() - 10);

    std::ostringstream ost;
    ost << std::hex << _bucket.getId();
    out << "(" << ost.str() << ", " << infoString << ", node " << _node.getIndex() << ", ideal " << _ideal_location_priority
        << (_trusted ? ", trusted" : "") << (_exists ? "" : ", new copy") << ")";
}

bool
BucketInstanceList::contains(lib::Node node) const {
    for (const auto & instance : _instances) {
        if (instance._node == node) return true;
    }
    return false;
}

void
BucketInstanceList::add(const BucketDatabase::Entry& e, const IdealServiceLayerNodesBundle::Node2Index & idealState)
{
    for (uint32_t i = 0; i < e.getBucketInfo().getNodeCount(); ++i) {
        const BucketCopy& copy(e.getBucketInfo().getNodeRef(i));
        lib::Node node(lib::NodeType::STORAGE, copy.getNode());
        _instances.emplace_back(e.getBucketId(), copy.getBucketInfo(), node, idealState.lookup(copy.getNode()), i, copy.trusted(), true);
    }
}

void
BucketInstanceList::populate(const document::BucketId& specificId, const DistributorBucketSpace& distributor_bucket_space, BucketDatabase& db)
{
    std::vector<BucketDatabase::Entry> entries;
    db.getParents(specificId, entries);
    for (const auto & entry : entries) {
        auto node2Index = distributor_bucket_space.get_ideal_service_layer_nodes_bundle(entry.getBucketId()).nonretired_or_maintenance_to_index();
        add(entry, node2Index);
    }
}

void
BucketInstanceList::removeNodeDuplicates()
{
    // Normally few entries in list. Probably cheaper to just go through entries
    // to detect whether it preexists rather than creating a set or similar.
    BucketInstanceList other;
    for (const auto& instance : _instances) {
        if (!other.contains(instance._node)) {
            other.add(instance);
        }
    }
    _instances.swap(other._instances);
}

void
BucketInstanceList::limitToRedundancyCopies(uint16_t redundancy)
{
    while (_instances.size() > redundancy) {
        _instances.pop_back();
    }
}

document::BucketId
BucketInstanceList::leastSpecificLeafBucketInSubtree(const document::BucketId& candidateId,
                                                     const document::BucketId& mostSpecificId,
                                                     const BucketDatabase& db)
{
    assert(candidateId.contains(mostSpecificId));
    document::BucketId treeNode = candidateId;
    // treeNode may reach at most 58 bits since buckets at 58 bits by definition
    // cannot have any children.
    while (db.childCount(treeNode) != 0) {
        treeNode = document::BucketId(treeNode.getUsedBits() + 1, mostSpecificId.getRawId());
    }
    assert(treeNode.contains(mostSpecificId));
    return treeNode;
}

void
BucketInstanceList::extendToEnoughCopies(const DistributorBucketSpace& distributor_bucket_space, const BucketDatabase& db,
                                         const document::BucketId& targetIfNonPreExisting, const document::BucketId& mostSpecificId)
{
    document::BucketId newTarget(_instances.empty() ? targetIfNonPreExisting : _instances[0]._bucket);
    newTarget = leastSpecificLeafBucketInSubtree(newTarget, mostSpecificId, db);

    const auto & idealNodes = distributor_bucket_space.get_ideal_service_layer_nodes_bundle(newTarget).available_nonretired_nodes();
    for (uint32_t i=0; i<idealNodes.size(); ++i) {
        lib::Node node(lib::NodeType::STORAGE, idealNodes[i]);
        if (!contains(node)) {
            // We don't sort `_instances` after extending, so just reuse `i` as dummy DB entry order.
            _instances.emplace_back(newTarget, api::BucketInfo(), node, i, i, false, false);
        }
    }
}

OperationTargetList
BucketInstanceList::createTargets(document::BucketSpace bucketSpace)
{
    OperationTargetList result;
    for (const auto& bi : _instances) {
        result.emplace_back(document::Bucket(bucketSpace, bi._bucket), bi._node, !bi._exists);
    }
    return result;
}

void
BucketInstanceList::print(vespalib::asciistream& out, const PrintProperties& p) const {
    vespalib::print(_instances, out, p);
}

namespace {

/**
 * To maintain a symmetry between which replicas receive Puts and which versions are
 * preferred for activation, use an identical ordering predicate for both (for the case
 * where replicas are for the same concrete bucket).
 *
 * Must only be used with BucketInstances that have a distinct _db_entry_order set per instance.
 */
struct ActiveReplicaSymmetricInstanceOrder {
    bool operator()(const BucketInstance& a, const BucketInstance& b) noexcept {
        if (a._bucket == b._bucket) {
            if (a._info.isReady() != b._info.isReady()) {
                return a._info.isReady();
            }
            if (a._info.getDocumentCount() != b._info.getDocumentCount()) {
                return a._info.getDocumentCount() > b._info.getDocumentCount();
            }
            if (a._ideal_location_priority != b._ideal_location_priority) {
                return a._ideal_location_priority < b._ideal_location_priority;
            }
            if (a._info.isActive() != b._info.isActive()) {
                return a._info.isActive();
            }
            // If all else is equal, this implies both A and B are on retired nodes, which is unlikely
            // but possible. Fall back to the existing DB _entry order_, which is equal to an ideal
            // state order where retired nodes are considered part of the ideal state (which is not the
            // case for most ideal state operations). Since the DB entry order is in ideal state order,
            // using this instead of node _index_ avoids affinities to lower indexes in such edge cases.
            return a._db_entry_order < b._db_entry_order;
        } else {
            // TODO this inconsistent split case is equal to the legacy logic (aside from the tie-breaking),
            //  but is considered to be extremely unlikely in practice, so not worth optimizing for.
            if ((a._info.getMetaCount() == 0) ^ (b._info.getMetaCount() == 0)) {
                return (a._info.getMetaCount() == 0);
            }
            if (a._bucket.getUsedBits() != b._bucket.getUsedBits()) {
                return (a._bucket.getUsedBits() > b._bucket.getUsedBits());
            }
            return a._db_entry_order < b._db_entry_order;
        }
        return false;
    }
};

/**
 * - Trusted copies should be preferred over non-trusted copies for the same bucket.
 * - Buckets in ideal locations should be preferred over non-ideal locations for the
 *   same bucket across several nodes.
 * - Buckets with data should be preferred over buckets without data.
 *
 * - Right after split/join, bucket is often not in ideal location, but should be
 *   preferred instead of source anyhow.
 */
struct LegacyInstanceOrder {
    bool operator()(const BucketInstance& a, const BucketInstance& b) noexcept {
        if (a._bucket == b._bucket) {
            // Trusted only makes sense within same bucket
            // Prefer trusted buckets over non-trusted ones.
            if (a._trusted != b._trusted) return a._trusted;
            if (a._ideal_location_priority != b._ideal_location_priority) {
                return a._ideal_location_priority < b._ideal_location_priority;
            }
        } else {
            if ((a._info.getMetaCount() == 0) ^ (b._info.getMetaCount() == 0)) {
                return (a._info.getMetaCount() == 0);
            }
            return (a._bucket.getUsedBits() > b._bucket.getUsedBits());
        }
        return false;
    }
};

} // anonymous

BucketInstanceList
OperationTargetResolverImpl::getAllInstances(OperationType type, const document::BucketId& id)
{
    BucketInstanceList instances;
    if (type == PUT) {
        instances.populate(id, _distributor_bucket_space, _bucketDatabase);
        if (_symmetric_replica_selection) {
            instances.sort(ActiveReplicaSymmetricInstanceOrder());
        } else {
            instances.sort(LegacyInstanceOrder());
        }
        instances.removeNodeDuplicates();
        instances.extendToEnoughCopies(_distributor_bucket_space, _bucketDatabase,
                                       _bucketDatabase.getAppropriateBucket(_minUsedBucketBits, id), id);
    } else {
        throw vespalib::IllegalArgumentException("Unsupported operation type given", VESPA_STRLOC);
    }
    return instances;
}

}
