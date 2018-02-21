// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statecheckers.h"
#include "activecopy.h"
#include <vespa/storage/distributor/operations/idealstate/splitoperation.h>
#include <vespa/storage/distributor/operations/idealstate/joinoperation.h>
#include <vespa/storage/distributor/operations/idealstate/removebucketoperation.h>
#include <vespa/storage/distributor/operations/idealstate/setbucketstateoperation.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storage/distributor/operations/idealstate/garbagecollectionoperation.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation.checkers");

using document::BucketSpace;

namespace storage {
namespace distributor {

bool
SplitBucketStateChecker::validForSplit(StateChecker::Context& c)
{
    // Can't split if we have no nodes.
    if (c.entry->getNodeCount() == 0) {
        LOG(spam,
            "Can't split bucket %s, since it has no copies",
            c.bucket.toString().c_str());
        return false;
    }

    // Can't split anymore if we already used 58 bits.
    if (c.getBucketId().getUsedBits() >= 58) {
        return false;
    }

    return true;
}

double
SplitBucketStateChecker::getBucketSizeRelativeToMax(StateChecker::Context& c)
{
    const BucketInfo& info(c.entry.getBucketInfo());
    const uint32_t highestDocumentCount(info.getHighestDocumentCount());
    const uint32_t highestTotalDocumentSize(info.getHighestTotalDocumentSize());
    const uint32_t highestMetaCount(info.getHighestMetaCount());
    const uint32_t highestUsedFileSize(info.getHighestUsedFileSize());

    if (highestDocumentCount < 2) {
        return 0;
    }

    double byteSplitRatio = 0;
    if (c.distributorConfig.getSplitSize() > 0) {
        byteSplitRatio = static_cast<double>(highestTotalDocumentSize)
                         / c.distributorConfig.getSplitSize();
    }

    double docSplitRatio = 0;
    if (c.distributorConfig.getSplitCount() > 0) {
        docSplitRatio = static_cast<double>(highestDocumentCount)
                        / c.distributorConfig.getSplitCount();
    }

    double fileSizeRatio = 0;
    if (c.distributorConfig.getSplitSize() > 0) {
        fileSizeRatio = static_cast<double>(highestUsedFileSize)
                         / (2 * c.distributorConfig.getSplitSize());
    }

    double metaSplitRatio = 0;
    if (c.distributorConfig.getSplitCount() > 0) {
        metaSplitRatio = static_cast<double>(highestMetaCount)
                        / (2 * c.distributorConfig.getSplitCount());
    }

    return std::max(std::max(byteSplitRatio, docSplitRatio),
                    std::max(fileSizeRatio, metaSplitRatio));
}

StateChecker::Result
SplitBucketStateChecker::generateMinimumBucketSplitOperation(
        StateChecker::Context& c)
{
    IdealStateOperation::UP so(new SplitOperation(
                c.component.getClusterName(),
                BucketAndNodes(c.getBucket(), c.entry->getNodes()),
                c.distributorConfig.getMinimalBucketSplit(),
                0,
                0));

    so->setPriority(c.distributorConfig.getMaintenancePriorities()
                    .splitDistributionBits);
    so->setDetailedReason(
            "[Splitting bucket because the current system size requires "
            "a higher minimum split bit]");
    return Result::createStoredResult(std::move(so), MaintenancePriority::MEDIUM);
}

StateChecker::Result
SplitBucketStateChecker::generateMaxSizeExceededSplitOperation(
        StateChecker::Context& c)
{
    IdealStateOperation::UP so(new SplitOperation(                
                c.component.getClusterName(),
                BucketAndNodes(c.getBucket(), c.entry->getNodes()),
                58,
                c.distributorConfig.getSplitCount(),
                c.distributorConfig.getSplitSize()));

    so->setPriority(c.distributorConfig.getMaintenancePriorities()
                    .splitLargeBucket);

    const BucketInfo& info(c.entry.getBucketInfo());
    vespalib::asciistream ost;
    ost << "[Splitting bucket because its maximum size ("
        << info.getHighestTotalDocumentSize()
        << " b, "
        << info.getHighestDocumentCount()
        << " docs, "
        << info.getHighestMetaCount()
        << " meta, "
        << info.getHighestUsedFileSize()
        << " b total"
        << ") is higher than the configured limit of ("
        << c.distributorConfig.getSplitSize()
        << ", " << c.distributorConfig.getSplitCount() << ")]";

    so->setDetailedReason(ost.str());
    return Result::createStoredResult(std::move(so), MaintenancePriority::HIGH);

}

StateChecker::Result
SplitBucketStateChecker::check(StateChecker::Context& c) {
    if (!validForSplit(c)) {
        return StateChecker::Result::noMaintenanceNeeded();
    }

    double splitRatio(getBucketSizeRelativeToMax(c));
    if (splitRatio > 1.0) {
        return generateMaxSizeExceededSplitOperation(c);
    }

    // Always split it if it has less used bits than the minimum.
    if (c.getBucketId().getUsedBits() < c.distributorConfig.getMinimalBucketSplit()) {
        return generateMinimumBucketSplitOperation(c);
    }
    return Result::noMaintenanceNeeded();
}

bool
JoinBucketsStateChecker::isFirstSibling(const document::BucketId& bucketId) const
{
    return (bucketId.getId() & (1ULL << (bucketId.getUsedBits() - 1))) == 0;
}

namespace {

bool
equalNodeSet(const std::vector<uint16_t>& idealState,
             const BucketDatabase::Entry& dbEntry)
{
    if (idealState.size() != dbEntry->getNodeCount()) {
        return false;
    }
    // Note: no assumptions are made on the ordering of the elements in
    // either vector.
    for (uint16_t node : idealState) {
        if (!dbEntry->getNode(node)) {
            return false;
        }
    }
    return true;
}

bool
bucketAndSiblingReplicaLocationsEqualIdealState(
        const StateChecker::Context& context)
{
    if (!equalNodeSet(context.idealState, context.entry)) {
        return false;
    }
    std::vector<uint16_t> siblingIdealState(
            context.distribution.getIdealStorageNodes(
                context.systemState, context.siblingBucket));
    if (!equalNodeSet(siblingIdealState, context.siblingEntry)) {
        return false;
    }
    return true;
}

bool
inconsistentJoinIsEnabled(const StateChecker::Context& context)
{
    return context.distributorConfig.getEnableInconsistentJoin();
}

bool
inconsistentJoinIsAllowed(const StateChecker::Context& context)
{
    return (inconsistentJoinIsEnabled(context)
            && bucketAndSiblingReplicaLocationsEqualIdealState(context));
}

} // anon ns

bool
JoinBucketsStateChecker::siblingsAreInSync(const Context& context) const
{
    const auto& entry(context.entry);
    const auto& siblingEntry(context.siblingEntry);

    if (entry->getNodeCount() != siblingEntry->getNodeCount()) {
        LOG(spam,
            "Not joining bucket %s because sibling bucket %s had different "
            "node count",
            context.bucket.toString().c_str(),
            context.siblingBucket.toString().c_str());
        return false;
    }

    bool siblingsCoLocated = true;
    for (uint32_t i = 0; i < entry->getNodeCount(); ++i) {
        if (entry->getNodeRef(i).getNode()
            != siblingEntry->getNodeRef(i).getNode())
        {
            siblingsCoLocated = false;
            break;
        }
    }

    if (!siblingsCoLocated && !inconsistentJoinIsAllowed(context)) {
        LOG(spam,
            "Not joining bucket %s because sibling bucket %s "
            "does not have the same node set, or inconsistent joins cannot be "
            "performed either due to config or because replicas were not in "
            "their ideal location",
            context.bucket.toString().c_str(),
            context.siblingBucket.toString().c_str());
        return false;
    }

    if (!entry->validAndConsistent() || !siblingEntry->validAndConsistent()) {
        LOG(spam,
            "Not joining bucket %s because it or %s is out of sync "
            "and syncing it may cause it to become too large",
            context.bucket.toString().c_str(),
            context.siblingBucket.toString().c_str());
        return false;
    }

    return true;
}

bool
JoinBucketsStateChecker::singleBucketJoinIsConsistent(const Context& c) const
{
    document::BucketId joinTarget(c.getBucketId().getUsedBits() - 1,
                                  c.getBucketId().getRawId());
    // If there are 2 children under the potential join target bucket, joining
    // would cause the bucket tree to become inconsistent. The reason for this
    // being that "moving" a bucket one bit up in the tree (and into
    // joinedBucket) would create a new parent bucket for the bucket(s)
    // already present in the other child tree, thus causing it to become
    // inconsistent. After all, we desire a bucket tree with only leaves
    // being actually present.
    return (c.db.childCount(joinTarget) == 1);
}

bool
JoinBucketsStateChecker::singleBucketJoinIsEnabled(const Context& c) const
{
    return c.distributorConfig.getEnableJoinForSiblingLessBuckets();
}

namespace {

// We don't want to invoke joins on buckets that have more replicas than
// required. This is in particular because joins cause ideal states to change
// for the target buckets and trigger merges. Since the removal of the non-
// ideal replicas is done by the DeleteBuckets state-checker, it will become
// preempted by potential follow-up joins unless we explicitly avoid these.
bool
contextBucketHasTooManyReplicas(const StateChecker::Context& c)
{
    return (c.entry->getNodeCount() > c.distribution.getRedundancy());
}

bool
bucketAtDistributionBitLimit(const document::BucketId& bucket,
                             const StateChecker::Context& c)
{
    return (bucket.getUsedBits() <= std::max(
                uint32_t(c.systemState.getDistributionBitCount()),
                c.distributorConfig.getMinimalBucketSplit()));
}

}

bool
JoinBucketsStateChecker::shouldJoin(const Context& c) const
{
    if (c.entry->getNodeCount() == 0) {
        LOG(spam, "Not joining bucket %s because it has no nodes",
            c.bucket.toString().c_str());
        return false;
    }

    if (contextBucketHasTooManyReplicas(c)) {
        LOG(spam, "Not joining %s because it has too high replication level",
            c.bucket.toString().c_str());
        return false;
    }

    if (c.distributorConfig.getJoinSize() == 0 && c.distributorConfig.getJoinCount() == 0) {
        LOG(spam, "Not joining bucket %s because join is disabled",
            c.bucket.toString().c_str());
        return false;
    }

    if (bucketAtDistributionBitLimit(c.getBucketId(), c)) {
        LOG(spam,
            "Not joining bucket %s because it is below the min split "
            "count (config: %u, cluster state: %u, bucket has: %u)",
            c.bucket.toString().c_str(),
            c.distributorConfig.getMinimalBucketSplit(),
            c.systemState.getDistributionBitCount(),
            c.getBucketId().getUsedBits());
        return false;
    }

    if (c.entry->hasRecentlyCreatedEmptyCopy()) {
        return false;
    }

    if (c.getSiblingEntry().valid()) {
        if (!isFirstSibling(c.getBucketId())) {
            LOG(spam,
                "Not joining bucket %s because it is the second sibling of "
                "%s and not the first",
                c.bucket.toString().c_str(),
                c.siblingBucket.toString().c_str());
            return false;
        }
        if (!siblingsAreInSync(c)) {
            return false;
        }
        return smallEnoughToJoin(c);
    }

    if (!singleBucketJoinIsEnabled(c)) {
        return false;
    }

    if (!smallEnoughToJoin(c)) {
        return false;
    }

    // No sibling and bucket has more bits than the minimum number of split
    // bits. If joining the bucket with itself into a bucket with 1 less
    // bit does _not_ introduce any inconsistencies in the bucket tree, do
    // so in order to gradually compact away sparse buckets.
    return singleBucketJoinIsConsistent(c);
}

/**
 * Compute sum(for each sibling(max(for each replica(used file size)))).
 * If sibling does not exist, treats its highest used file size as 0.
 */
uint64_t
JoinBucketsStateChecker::getTotalUsedFileSize(const Context& c) const
{
    return (c.entry.getBucketInfo().getHighestUsedFileSize()
            + c.getSiblingEntry().getBucketInfo().getHighestUsedFileSize());
}

/**
 * Compute sum(for each sibling(max(for each replica(meta count)))).
 * If sibling does not exist, treats its highest meta count as 0.
 */
uint64_t
JoinBucketsStateChecker::getTotalMetaCount(const Context& c) const
{
    return (c.entry.getBucketInfo().getHighestMetaCount()
            + c.getSiblingEntry().getBucketInfo().getHighestMetaCount());
}

bool
JoinBucketsStateChecker::smallEnoughToJoin(const Context& c) const
{
    if (c.distributorConfig.getJoinSize() != 0) {
        if (getTotalUsedFileSize(c) >= c.distributorConfig.getJoinSize()) {
            return false;
        }
    }
    if (c.distributorConfig.getJoinCount() != 0) {
        if (getTotalMetaCount(c) >= c.distributorConfig.getJoinCount()) {
            return false;
        }
    }
    return true;
}

namespace {

bool
legalBucketSplitLevel(const document::BucketId& bucket,
                      const StateChecker::Context& c)
{
    return bucket.getUsedBits() >= c.distributorConfig.getMinimalBucketSplit();
}

bool
bucketHasMultipleChildren(const document::BucketId& bucket,
                          const StateChecker::Context& c)
{
    return c.db.childCount(bucket) > 1;
}

}

document::Bucket
JoinBucketsStateChecker::computeJoinBucket(const Context& c) const
{
    // Always decrease by at least 1 bit, as we could not get here unless this
    // were a valid outcome.
    unsigned int level = c.getBucketId().getUsedBits() - 1;
    document::BucketId target(level, c.getBucketId().getRawId());

    // Push bucket up the tree as long as it gets no siblings. This means
    // joins involving 2 source buckets will currently only be decreased by 1
    // bit (mirroring the legacy behavior), but sparse (single) buckets may
    // be decreased by multiple bits. We may want to optimize joins for cases
    // with 2 source buckets in the future.
    while (true) {
        document::BucketId candidate(level, c.getBucketId().getRawId());
        if (bucketHasMultipleChildren(candidate, c)
            || !legalBucketSplitLevel(candidate, c))
        {
            break;
        }
        --level;
        target = candidate;
    }
    return document::Bucket(c.getBucket().getBucketSpace(), target);
}

StateChecker::Result
JoinBucketsStateChecker::check(StateChecker::Context& c)
{
    // At this point in time, bucket is consistently split as the state checker
    // would otherwise be pre-empted by the inconsistent state checker.
    if (!shouldJoin(c)) {
        return Result::noMaintenanceNeeded();
    }
    
    document::Bucket joinedBucket(computeJoinBucket(c));
    assert(joinedBucket.getBucketId().getUsedBits() < c.getBucketId().getUsedBits());

    std::vector<document::BucketId> sourceBuckets;
    if (c.getSiblingEntry().valid()) {
        sourceBuckets.push_back(c.siblingBucket);
    } else {
        sourceBuckets.push_back(c.getBucketId());
    }
    sourceBuckets.push_back(c.getBucketId());
    IdealStateOperation::UP op(new JoinOperation(
            c.component.getClusterName(),
            BucketAndNodes(joinedBucket, c.entry->getNodes()),
            sourceBuckets));
    op->setPriority(c.distributorConfig.getMaintenancePriorities()
                    .joinBuckets);
    vespalib::asciistream ost;
    ost << "[Joining buckets "
        << sourceBuckets[1].toString()
        << " and " << sourceBuckets[0].toString()
        << " because their size ("
        << getTotalUsedFileSize(c) 
        << " bytes, "
        << getTotalMetaCount(c)
        << " docs) is less than the configured limit of ("
        << c.distributorConfig.getJoinSize()
        << ", "
        << c.distributorConfig.getJoinCount()
        << ")";

    op->setDetailedReason(ost.str());

    return Result::createStoredResult(std::move(op), MaintenancePriority::VERY_LOW);
}

bool
SplitInconsistentStateChecker::isLeastSplitBucket(
        const document::BucketId& bucket,
        const std::vector<BucketDatabase::Entry>& entries) const
{
    // Figure out if any other buckets are less split than the current one.
    for (uint32_t i = 0; i < entries.size(); ++i) {
        const BucketDatabase::Entry& e = entries[i];

        assert(e.valid());

        if (e.getBucketId().getUsedBits() < bucket.getUsedBits()) {
            return false;
        }
    }

    return true;
}

uint32_t
SplitInconsistentStateChecker::getHighestUsedBits(
        const std::vector<BucketDatabase::Entry>& entries) const
{
    uint32_t highestUsedBits = 0;
    for (uint32_t i = 0; i < entries.size(); ++i) {
        highestUsedBits = std::max(entries[i].getBucketId().getUsedBits(),
                                   highestUsedBits);
    }
    return highestUsedBits;
}

vespalib::string
SplitInconsistentStateChecker::getReason(
        const document::BucketId& bucketId,
        const std::vector<BucketDatabase::Entry>& entries) const
{
    vespalib::asciistream reason;
    reason << "[Bucket is inconsistently split (list includes "
           << vespalib::hex << "0x" << bucketId.getId();

    for (uint32_t i = 0, found = 0; i < entries.size() && found < 3; i++) {
        if (!(entries[i].getBucketId() == bucketId)) {
            reason << ", 0x" << vespalib::hex << entries[i].getBucketId().getId();
            ++found;
        }
    }

    if (entries.size() > 4) {
        reason << " and " << vespalib::dec << entries.size() - 4 << " others";
    }

    reason << ") Splitting it to improve the problem (max used bits "
           << vespalib::dec
           << getHighestUsedBits(entries)
           << ")]";

    return reason.str();
}

namespace {

bool
isInconsistentlySplit(const StateChecker::Context& c)
{
    return (c.entries.size() > 1);
}

}

StateChecker::Result
SplitInconsistentStateChecker::check(StateChecker::Context& c)
{
    if (!isInconsistentlySplit(c)) {
        return Result::noMaintenanceNeeded();
    }

    if (!isLeastSplitBucket(c.getBucketId(), c.entries)) {
        return Result::noMaintenanceNeeded();
    }
    
    IdealStateOperation::UP op(new SplitOperation(
            c.component.getClusterName(),
            BucketAndNodes(c.getBucket(), c.entry->getNodes()),
            getHighestUsedBits(c.entries),
            0,
            0));

    op->setPriority(c.distributorConfig.getMaintenancePriorities()
                    .splitInconsistentBucket);
    op->setDetailedReason(getReason(c.getBucketId(), c.entries));
    return Result::createStoredResult(std::move(op), MaintenancePriority::HIGH);
}

namespace {
bool containsMaintenanceNode(const std::vector<uint16_t>& ideal,
                             const StateChecker::Context& c)
{
    for (uint32_t i = 0; i < ideal.size(); i++) {
        if (c.systemState.getNodeState(lib::Node(lib::NodeType::STORAGE,
                                                    ideal[i])).getState()
                == lib::State::MAINTENANCE)
        {
            return true;
        }
    }

    return false;
}

bool
consistentApartFromEmptyBucketsInNonIdealLocationAndInvalidEntries(
        const std::vector<uint16_t>& idealNodes,
        const BucketInfo& entry)
{
    api::BucketInfo info;
    for (uint32_t i=0, n=entry.getNodeCount(); i<n; ++i) {
        const BucketCopy& copy(entry.getNodeRef(i));
        bool onIdealNode = false;
        for (uint32_t j = 0; j < idealNodes.size(); ++j) {
            if (copy.getNode() == idealNodes[j]) {
                onIdealNode = true;
                break;
            }
        }
            // Ignore empty buckets on non-ideal nodes
        if (!onIdealNode && copy.empty()) {
            continue;
        }
            // Ignore invalid entries.
        if (!copy.valid()) {
            continue;
        }
        if (info.valid()) {
            if (info.getChecksum() != copy.getChecksum()) {
                return false;
            }
        } else {
            info = copy.getBucketInfo();
        }
    }
    return true;
}

class MergeNodes
{
public:
    MergeNodes()
        : _reason(), _nodes(), _problemFlags(0), _priority(255)
    {}

    MergeNodes(const BucketDatabase::Entry& entry)
        : _reason(), _nodes(), _problemFlags(0), _priority(255)
    {
        for (uint16_t i = 0; i < entry->getNodeCount(); i++) {
            addNode(entry->getNodeRef(i).getNode());
        }
    }

    ~MergeNodes();

    void operator+=(const MergeNodes& other) {
        _reason << other._reason.str();
        _problemFlags |= other._problemFlags;
        _nodes.insert(_nodes.end(), other._nodes.begin(), other._nodes.end());
        updatePriority(other._priority);
    }

    bool shouldMerge() const {
        return _problemFlags != 0;
    }

    void markMoveToIdealLocation(uint16_t node, uint8_t msgPriority) {
        _reason << "[Moving bucket to ideal node " << node << "]";
        addProblem(NON_IDEAL_LOCATION);
        addNode(node);
        updatePriority(msgPriority);
    }

    void markOutOfSync(const StateChecker::Context& c, uint8_t msgPriority) {
        _reason << "[Synchronizing buckets with different checksums "
                << c.entry->toString()
                << "]";
        addProblem(OUT_OF_SYNC);
        updatePriority(msgPriority);
    }

    void markMissingReplica(uint16_t node, uint8_t msgPriority) {
        _reason << "[Adding missing node " << node << "]";
        addProblem(MISSING_REPLICA);
        addNode(node);
        updatePriority(msgPriority);
    }

    bool needsMoveOnly() const {
        return _problemFlags == NON_IDEAL_LOCATION;
    }

    void addNode(uint16_t node) {
        _nodes.push_back(node);
    }

    const std::vector<uint16_t>& nodes() const noexcept { return _nodes; }
    uint8_t priority() const noexcept { return _priority; }
    std::string reason() const { return _reason.str(); }

private:
    void updatePriority(uint8_t pri) {
        _priority = std::min(pri, _priority);
    }

    void addProblem(uint8_t newProblem) {
        _problemFlags |= newProblem;
    }

    enum Problem {
        OUT_OF_SYNC = 1,
        MISSING_REPLICA = 2,
        NON_IDEAL_LOCATION = 4
    };
    vespalib::asciistream _reason;
    std::vector<uint16_t> _nodes;
    uint8_t _problemFlags;
    uint8_t _priority;
};

MergeNodes::~MergeNodes() {}

bool
presentInIdealState(const StateChecker::Context& c, uint16_t node)
{
    return c.unorderedIdealState.find(node) != c.unorderedIdealState.end();
}

void
addStatisticsForNonIdealNodes(const StateChecker::Context& c,
                              bool missingReplica)
{
    // Common case is that ideal state == actual state with no missing replicas.
    // If so, do nothing.
    if (!missingReplica && (c.idealState.size() == c.entry->getNodeCount())) {
        return;
    }
    for (uint32_t j = 0; j < c.entry->getNodeCount(); ++j) {
        const uint16_t node(c.entry->getNodeRef(j).getNode());
        if (!presentInIdealState(c, node)) {
            c.stats.incMovingOut(node, c.getBucketSpace());
        } else if (missingReplica) {
            // Copy is in ideal location and we're missing a replica. Thus
            // we treat all ideal copies as sources to copy from.
            c.stats.incCopyingOut(node, c.getBucketSpace());
        }
    }
}

MergeNodes
checkForNodesMissingFromIdealState(StateChecker::Context& c)
{
    MergeNodes ret;

    // Check if we need to add copies to get to ideal state.
    if (!c.entry->emptyAndConsistent()) {
        bool hasMissingReplica = false;
        for (uint32_t i = 0; i < c.idealState.size(); i++) {
            bool found = false;
            for (uint32_t j = 0; j < c.entry->getNodeCount(); j++) {
                if (c.entry->getNodeRef(j).getNode() == c.idealState[i]) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                const DistributorConfiguration::MaintenancePriorities& mp(
                        c.distributorConfig.getMaintenancePriorities());
                if (c.idealState.size() > c.entry->getNodeCount()) {
                    ret.markMissingReplica(c.idealState[i],
                                           mp.mergeTooFewCopies);
                } else {
                    ret.markMoveToIdealLocation(c.idealState[i],
                                                mp.mergeMoveToIdealNode);
                }
                c.stats.incCopyingIn(c.idealState[i], c.getBucketSpace());
                hasMissingReplica = true;
            }
        }
        addStatisticsForNonIdealNodes(c, hasMissingReplica);
    }
    return ret;
}

void
addStatisticsForOutOfSyncCopies(StateChecker::Context& c)
{
    const uint32_t n = c.entry->getNodeCount();
    for (uint32_t i = 0; i < n; ++i) {
        const BucketCopy& cp(c.entry->getNodeRef(i));
        c.stats.incSyncing(cp.getNode(), c.getBucketSpace());
    }
}

MergeNodes
checkIfBucketsAreOutOfSyncAndNeedMerging(StateChecker::Context& c)
{
    MergeNodes ret;
    if (!consistentApartFromEmptyBucketsInNonIdealLocationAndInvalidEntries(
                c.idealState,
                c.entry.getBucketInfo()))
    {
        auto pri(c.distributorConfig.getMaintenancePriorities()
                     .mergeOutOfSyncCopies);
        ret.markOutOfSync(c, pri);
        addStatisticsForOutOfSyncCopies(c);
    }
    return ret;
}

bool
allCopiesAreInvalid(const StateChecker::Context& c)
{
    const uint32_t n = c.entry->getNodeCount();
    for (uint32_t i = 0; i < n; ++i) {
        const BucketCopy& cp(c.entry->getNodeRef(i));
        if (cp.valid()) {
            return false;
        }
    }
    return true;
}

}

StateChecker::Result
SynchronizeAndMoveStateChecker::check(StateChecker::Context& c)
{
    if (isInconsistentlySplit(c)) {
        return Result::noMaintenanceNeeded();
    }
    if (containsMaintenanceNode(c.idealState, c)) {
        return Result::noMaintenanceNeeded();
    }
    if (allCopiesAreInvalid(c)) {
        return Result::noMaintenanceNeeded();
    }

    assert(c.entry->getNodeCount() > 0);

    MergeNodes result(c.entry);
    result += checkForNodesMissingFromIdealState(c);
    result += checkIfBucketsAreOutOfSyncAndNeedMerging(c);

    if (result.shouldMerge()) {
        IdealStateOperation::UP op(
                new MergeOperation(BucketAndNodes(c.getBucket(), result.nodes()),
                                   c.distributorConfig.getMaxNodesPerMerge()));
        op->setPriority(result.priority());
        op->setDetailedReason(result.reason());
        MaintenancePriority::Priority schedPri(
                result.needsMoveOnly() ? MaintenancePriority::LOW
                                       : MaintenancePriority::MEDIUM);

        return Result::createStoredResult(std::move(op), schedPri);
    } else {
        LOG(spam, "Bucket %s: No need for merge, as bucket is in consistent state "
            "(or inconsistent buckets are empty) %s",
            c.bucket.toString().c_str(),
            c.entry->toString().c_str());
        return Result::noMaintenanceNeeded();
    }
}

bool
DeleteExtraCopiesStateChecker::bucketHasNoData(const StateChecker::Context& c)
{
    return (c.entry->getHighestMetaCount() == 0
            && !c.entry->hasRecentlyCreatedEmptyCopy());
}

bool
DeleteExtraCopiesStateChecker::copyIsInIdealState(const BucketCopy& cp,
                                                  const StateChecker::Context& c) const
{
    return hasItem(c.idealState, cp.getNode());
}

bool
DeleteExtraCopiesStateChecker::enoughCopiesKept(uint32_t keptIdealCopies,
                                                uint32_t keptNonIdealCopies,
                                                const StateChecker::Context& c) const
{
    return ((keptIdealCopies + keptNonIdealCopies) >= c.distribution.getRedundancy());
}

void
DeleteExtraCopiesStateChecker::addToRemoveSet(
        const BucketCopy& copyToRemove,
        const char* reasonForRemoval,
        std::vector<uint16_t>& removedCopies,
        vespalib::asciistream& reasons)
{
    reasons << "[Removing " << reasonForRemoval
            << " from node " << copyToRemove.getNode()
            << ']';
    removedCopies.push_back(copyToRemove.getNode());
}

uint32_t
DeleteExtraCopiesStateChecker::numberOfIdealCopiesPresent(
        const StateChecker::Context& c) const
{
    const uint32_t cnt = c.entry->getNodeCount();
    uint32_t idealCopies = 0;
    for (uint32_t i = 0; i < cnt; ++i) {
        const BucketCopy& cp(c.entry->getNodeRef(i));
        if (copyIsInIdealState(cp, c)) {
            ++idealCopies;
        }
    }
    return idealCopies;
}

/**
 * Delete copies that are not in ideal state and either:
 *  - in sync with all other copies AND redundant, or
 *  - empty
 *
 * Assumes that no other method has removed copies before this.
 */
void
DeleteExtraCopiesStateChecker::removeRedundantEmptyOrConsistentCopies(
        StateChecker::Context& c,
        std::vector<uint16_t>& removedCopies,
        vespalib::asciistream& reasons)
{
    assert(removedCopies.empty());
    const bool copiesAreConsistent = c.entry->validAndConsistent();
    const uint32_t cnt = c.entry->getNodeCount();
    // Always keep all ideal copies
    uint32_t keptIdealCopies = numberOfIdealCopiesPresent(c);
    uint32_t keptNonIdealCopies = 0;

    for (uint32_t i = 0; i < cnt; ++i) {
        const BucketCopy& cp(c.entry->getNodeRef(i));
        if (copyIsInIdealState(cp, c)) {
            continue;
        }
        // Caller already checked for recently created/invalid copies, so
        // any empty copies not in ideal state are pending for a bending,
        // no matter if bucket is consistent or not.
        if (cp.empty()) {
            addToRemoveSet(cp, "empty copy", removedCopies, reasons);
        } else if (copiesAreConsistent
                   && enoughCopiesKept(keptIdealCopies, keptNonIdealCopies, c)
                   && !cp.active())
        {
            addToRemoveSet(cp, "redundant in-sync copy",
                           removedCopies, reasons);
        } else {
            ++keptNonIdealCopies;
        }
    }
}

StateChecker::Result
DeleteExtraCopiesStateChecker::check(StateChecker::Context& c)
{
    if (c.entry->hasInvalidCopy()) {
        // Don't delete anything here.
        return Result::noMaintenanceNeeded();
    }
    // Maintain symmetry with merge; don't try to mess with nodes that have an
    // ideal copy on a node set in maintenance mode.
    if (containsMaintenanceNode(c.idealState, c)) {
        return Result::noMaintenanceNeeded();
    }

    vespalib::asciistream reasons;
    std::vector<uint16_t> removedCopies;

    if (bucketHasNoData(c)) {
        reasons << "[Removing all copies since bucket is empty:"
                << c.entry->toString() << "]";

        for (uint32_t j = 0, cnt = c.entry->getNodeCount(); j < cnt; ++j) {
            removedCopies.push_back(c.entry->getNodeRef(j).getNode());
        }
    } else if (c.entry->getNodeCount() <= c.distribution.getRedundancy()) {
        return Result::noMaintenanceNeeded();
    } else if (c.entry->hasRecentlyCreatedEmptyCopy()) {
        return Result::noMaintenanceNeeded();
    } else {
        removeRedundantEmptyOrConsistentCopies(c, removedCopies, reasons);
    }

    if (!removedCopies.empty()) {
        IdealStateOperation::UP ro(new RemoveBucketOperation(
                c.component.getClusterName(),
                BucketAndNodes(c.getBucket(), removedCopies)));

        ro->setPriority(c.distributorConfig.getMaintenancePriorities()
                        .deleteBucketCopy);
        ro->setDetailedReason(reasons.str());
        return Result::createStoredResult(std::move(ro), MaintenancePriority::HIGH);
    }

    return Result::noMaintenanceNeeded();
}

bool
BucketStateStateChecker::shouldSkipActivationDueToMaintenance(
        const ActiveList& activeNodes,
        const StateChecker::Context& c) const
{
    for (uint32_t i = 0; i < activeNodes.size(); ++i) {
        const BucketCopy* cp(c.entry->getNode(activeNodes[i].nodeIndex));
        if (!cp || cp->active()) {
            continue;
        }
        if (!cp->ready()) {
            // If copy is not ready, we don't want to activate it if a node
            // is set in maintenance. Doing so would imply that we want proton
            // to start background indexing.
            return containsMaintenanceNode(c.idealState, c);
        }
    }
    return false;
}

/**
 * The copy we want to set active is, in prioritized order:
 *  1. The first ideal state copy that is trusted and ready
 *  2. The first non-ideal state copy that is ready
 *  3. The first ideal state copy that is trusted
 *  4. The first available copy that is trusted
 *  5. The first ideal state copy
 *  6. Any existing active copy (i.e. do not alter active state)
 *  7. Any valid copy if no copies are active
 */
StateChecker::Result
BucketStateStateChecker::check(StateChecker::Context& c)
{
    if (c.distributorConfig.isBucketActivationDisabled()) {
        return Result::noMaintenanceNeeded();
    }

    if (isInconsistentlySplit(c)) {
        return Result::noMaintenanceNeeded();
    }

    ActiveList activeNodes(
            ActiveCopy::calculate(c.idealState, c.distribution, c.entry));
    if (activeNodes.empty()) {
        return Result::noMaintenanceNeeded();
    }
    if (shouldSkipActivationDueToMaintenance(activeNodes, c)) {
        return Result::noMaintenanceNeeded();
    }

    vespalib::asciistream reason;
    std::vector<uint16_t> operationNodes;
    for (uint32_t i=0; i<activeNodes.size(); ++i) {
        const BucketCopy* cp = c.entry->getNode(activeNodes[i].nodeIndex);
        if (cp == 0 || cp->active()) {
            continue;
        }
        operationNodes.push_back(activeNodes[i].nodeIndex);
        reason << "[Setting node " << activeNodes[i].nodeIndex << " as active: "
               << activeNodes[i].reason << "]";
    }

    // Deactivate all copies that are currently marked as active.
    for (uint32_t i = 0; i < c.entry->getNodeCount(); ++i) {
        const BucketCopy& cp = c.entry->getNodeRef(i);
        if (!cp.active()) {
            continue;
        }
        bool shouldBeActive = false;
        for (uint32_t j=0; j<activeNodes.size(); ++j) {
            if (activeNodes[j].nodeIndex == cp.getNode()) {
                shouldBeActive = true;
            }
        }
        if (!shouldBeActive) {
            reason << "[Setting node " << cp.getNode() << " as inactive]";
            operationNodes.push_back(cp.getNode());
        }
    }

    if (operationNodes.size() == 0) {
        return Result::noMaintenanceNeeded();
    }

    std::vector<uint16_t> activeNodeIndexes;
    for (uint32_t i=0; i<activeNodes.size(); ++i) {
        activeNodeIndexes.push_back(activeNodes[i].nodeIndex);
    }
    auto op = std::make_unique<SetBucketStateOperation>(
            c.component.getClusterName(),
            BucketAndNodes(c.getBucket(), operationNodes),
            activeNodeIndexes);

    // If activeNodes > 1, we're dealing with a active-per-leaf group case and
    // we currently always send high pri activations.
    // Otherwise, only > 1 operationNodes if we have copies to deactivate.
    if (activeNodes.size() > 1 || operationNodes.size() == 1) {
        op->setPriority(c.distributorConfig.getMaintenancePriorities()
                        .activateNoExistingActive);
    } else {
        op->setPriority(c.distributorConfig.getMaintenancePriorities()
                        .activateWithExistingActive);
    }
    op->setDetailedReason(reason.str());
    return Result::createStoredResult(std::move(op), MaintenancePriority::VERY_HIGH);
}

bool
GarbageCollectionStateChecker::needsGarbageCollection(const Context& c) const
{
    if (c.entry->getNodeCount() == 0 || c.distributorConfig.getGarbageCollectionInterval() == 0) {
        return false;
    }
    if (containsMaintenanceNode(c.idealState, c)) {
        return false;
    }
    std::chrono::seconds lastRunAt(c.entry->getLastGarbageCollectionTime());
    std::chrono::seconds currentTime(
            c.component.getClock().getTimeInSeconds().getTime());

    return c.gcTimeCalculator.shouldGc(c.getBucketId(), currentTime, lastRunAt);
}

StateChecker::Result
GarbageCollectionStateChecker::check(Context& c)
{
    if (needsGarbageCollection(c)) {
        IdealStateOperation::UP op(
                new GarbageCollectionOperation(
                        c.component.getClusterName(),
                        BucketAndNodes(c.getBucket(), c.entry->getNodes())));

        vespalib::asciistream reason;
        reason << "[Needs garbage collection: Last check at "
               << c.entry->getLastGarbageCollectionTime()
               << ", current time "
               << c.component.getClock().getTimeInSeconds().getTime()
               << ", configured interval "
               << c.distributorConfig.getGarbageCollectionInterval() << "]";

        op->setPriority(c.distributorConfig.getMaintenancePriorities()
                        .garbageCollection);
        op->setDetailedReason(reason.c_str());
        return Result::createStoredResult(std::move(op), MaintenancePriority::VERY_LOW);
    } else {
        return Result::noMaintenanceNeeded();
    }
}

} // distributor
} // storage
