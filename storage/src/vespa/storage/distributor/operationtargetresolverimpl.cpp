// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationtargetresolverimpl.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/printable.hpp>
#include <sstream>
#include <cassert>

namespace storage::distributor {

BucketInstance::BucketInstance(
        const document::BucketId& id, const api::BucketInfo& info,
        lib::Node node, uint16_t idealLocationPriority, bool trusted, bool exist)
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
    out << "(" << ost.str() << ", "
        << infoString << ", node " << _node.getIndex()
        << ", ideal " << _idealLocationPriority
        << (_trusted ? ", trusted" : "")
        << (_exist ? "" : ", new copy")
        << ")";
}

bool
BucketInstanceList::contains(lib::Node node) const {
    for (uint32_t i=0; i<_instances.size(); ++i) {
        if (_instances[i]._node == node) return true;
    }
    return false;
}

void
BucketInstanceList::add(BucketDatabase::Entry& e,
                        const lib::IdealNodeList& idealState)
{
    for (uint32_t i = 0; i < e.getBucketInfo().getNodeCount(); ++i) {
        const BucketCopy& copy(e.getBucketInfo().getNodeRef(i));
        lib::Node node(lib::NodeType::STORAGE, copy.getNode());
        _instances.push_back(BucketInstance(
                e.getBucketId(), copy.getBucketInfo(), node,
                idealState.indexOf(node), copy.trusted()));
    }
}

void
BucketInstanceList::populate(const document::BucketId& specificId, BucketDatabase& db,
                             const lib::IdealNodeCalculator& idealNodeCalc)
{
    std::vector<BucketDatabase::Entry> entries;
    db.getParents(specificId, entries);
    for (uint32_t i=0; i<entries.size(); ++i) {
        lib::IdealNodeList idealNodes(idealNodeCalc.getIdealStorageNodes(
                entries[i].getBucketId(),
                lib::IdealNodeCalculator::UpInitMaintenance));
        add(entries[i], idealNodes);
    }
}

void
BucketInstanceList::removeNodeDuplicates()
{
        // Normally few entries in list. Probably heaper to just go through entries
        // to detect whether it pre exist rather than creating a set or similar.
    BucketInstanceList other;
    for (uint32_t i=0; i<_instances.size(); ++i) {
        BucketInstance& instance(_instances[i]);
        if (!other.contains(instance._node)) {
            other.add(instance);
        }
    }
    _instances.swap(other._instances);
}

void
BucketInstanceList::limitToRedundancyCopies(uint16_t redundancy)
{
    while (_instances.size() > redundancy) _instances.pop_back();
}

document::BucketId
BucketInstanceList::leastSpecificLeafBucketInSubtree(
        const document::BucketId& candidateId,
        const document::BucketId& mostSpecificId,
        const BucketDatabase& db) const
{
    assert(candidateId.contains(mostSpecificId));
    document::BucketId treeNode = candidateId;
    // treeNode may reach at most 58 bits since buckets at 58 bits by definition
    // cannot have any children.
    while (db.childCount(treeNode) != 0) {
        treeNode = document::BucketId(treeNode.getUsedBits() + 1,
                                      mostSpecificId.getRawId());
    }
    assert(treeNode.contains(mostSpecificId));
    return treeNode;
}

void
BucketInstanceList::extendToEnoughCopies(
        const BucketDatabase& db,
        const document::BucketId& targetIfNonPreExisting,
        const document::BucketId& mostSpecificId,
        const lib::IdealNodeCalculator& idealNodeCalc)
{
    document::BucketId newTarget(_instances.empty() ? targetIfNonPreExisting
                                                    : _instances[0]._bucket);
    newTarget = leastSpecificLeafBucketInSubtree(newTarget, mostSpecificId, db);

    lib::IdealNodeList idealNodes(idealNodeCalc.getIdealStorageNodes(
            newTarget, lib::IdealNodeCalculator::UpInit));
    for (uint32_t i=0; i<idealNodes.size(); ++i) {
        if (!contains(idealNodes[i])) {
            _instances.push_back(BucketInstance(
                    newTarget, api::BucketInfo(), idealNodes[i],
                    i, false, false));
        }
    }
}

OperationTargetList
BucketInstanceList::createTargets()
{
    OperationTargetList result;
    for (uint32_t i=0; i<_instances.size(); ++i) {
        BucketInstance& bi(_instances[i]);
        result.push_back(OperationTarget(bi._bucket, bi._node, !bi._exist));
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
OperationTargetResolverImpl::getAllInstances(OperationType type,
                                             const document::BucketId& id)
{
    BucketInstanceList instances;
    if (type == PUT) {
        instances.populate(id, _bucketDatabase, _idealNodeCalculator);
        instances.sort(InstanceOrder());
        instances.removeNodeDuplicates();
        instances.extendToEnoughCopies(
                _bucketDatabase,
                _bucketDatabase.getAppropriateBucket(_minUsedBucketBits, id),
                id,
                _idealNodeCalculator);
    } else {
        throw vespalib::IllegalArgumentException(
                "Unsupported operation type given", VESPA_STRLOC);
    }
    return instances;
}

}
