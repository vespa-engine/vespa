// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationtargetresolverimpl.h"
#include "distributor_bucket_space.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/printable.hpp>
#include <sstream>
#include <cassert>

namespace storage::distributor {

BucketInstance::BucketInstance(const document::BucketId& id, const api::BucketInfo& info, lib::Node node,
                               uint16_t idealLocationPriority, bool trusted, bool exist) noexcept
    : _bucket(id), _info(info), _node(node),
      _idealLocationPriority(idealLocationPriority), _trusted(trusted), _exist(exist)
{
}

void
BucketInstance::print(vespalib::asciistream& out, const PrintProperties&) const
{
    std::string infoString(_info.toString());
    infoString = infoString.substr(10, infoString.size() - 10);

    std::ostringstream ost;
    ost << std::hex << _bucket.getId();
    out << "(" << ost.str() << ", " << infoString << ", node " << _node.getIndex() << ", ideal " << _idealLocationPriority
        << (_trusted ? ", trusted" : "") << (_exist ? "" : ", new copy") << ")";
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
        _instances.emplace_back(e.getBucketId(), copy.getBucketInfo(), node, idealState.lookup(copy.getNode()), copy.trusted(), true);
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
            _instances.emplace_back(newTarget, api::BucketInfo(), node, i, false, false);
        }
    }
}

OperationTargetList
BucketInstanceList::createTargets(document::BucketSpace bucketSpace)
{
    OperationTargetList result;
    for (const auto& bi : _instances) {
        result.emplace_back(document::Bucket(bucketSpace, bi._bucket), bi._node, !bi._exist);
    }
    return result;
}

void
BucketInstanceList::print(vespalib::asciistream& out, const PrintProperties& p) const {
    vespalib::print(_instances, out, p);
}

namespace {

/**
 * - Trusted copies should be preferred over non-trusted copies for the same bucket.
 * - Buckets in ideal locations should be preferred over non-ideal locations for the
 *   same bucket across several nodes.
 * - Buckets with data should be preferred over buckets without data.
 *
 * - Right after split/join, bucket is often not in ideal location, but should be
 *   preferred instead of source anyhow.
 */
struct InstanceOrder {
    bool operator()(const BucketInstance& a, const BucketInstance& b) {
        if (a._bucket == b._bucket) {
                // Trusted only makes sense within same bucket
                // Prefer trusted buckets over non-trusted ones.
            if (a._trusted != b._trusted) return a._trusted;
            if (a._idealLocationPriority != b._idealLocationPriority) {
                return a._idealLocationPriority < b._idealLocationPriority;
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
        instances.sort(InstanceOrder());
        instances.removeNodeDuplicates();
        instances.extendToEnoughCopies(_distributor_bucket_space, _bucketDatabase,
                                       _bucketDatabase.getAppropriateBucket(_minUsedBucketBits, id), id);
    } else {
        throw vespalib::IllegalArgumentException("Unsupported operation type given", VESPA_STRLOC);
    }
    return instances;
}

}
