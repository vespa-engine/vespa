// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <map>
#include <queue>
#include <vespa/storage/distributor/operationtargetresolver.h>
#include <vespa/storage/distributor/bucketdb/bucketdatabase.h>
#include <vespa/vespalib/stllike/hash_set.h>

namespace storage {
namespace distributor {

using document::BucketId;

namespace {

struct BucketState
{
    BucketId bid;
    BucketCopy copy;

    BucketState(const BucketId& id, const BucketCopy& cpy)
        : bid(id), copy(cpy)
    {
    }
};

enum Priority
{
    NONE_FOUND,
    NOT_TRUSTED_OR_IDEAL,
    IDEAL_STATE_NOT_TRUSTED,
    TRUSTED,
};

class NodePriority
{
public:
    NodePriority(const BucketState* state,
                   Priority priority,
                   uint16_t node)
        : _state(state),
          _priority(priority),
          _node(node)
    {
    }

    bool operator<(const NodePriority& other) const {
        return _priority < other._priority;
    }

    const BucketState* getState() const { return _state; }
    Priority getPriority() const { return _priority; }
    uint16_t getNode() const { return _node; }

    bool isValid() const { return _state != 0; }

    void reset(const BucketState* state, Priority priority) {
        _state = state;
        _priority = priority;
    }

    bool worseThan(Priority otherPri) const {
        return _priority < otherPri;
    }
    bool equalTo(Priority otherPri) const {
        return _priority == otherPri;
    }
    /**
     * Returns true iff the current best bucket has fewer used bits
     * than the parameter bucket. Requires current best bucket to be
     * set already.
     */
    bool lessSplitThan(const BucketId& bid) const {
        return _state->bid.getUsedBits() < bid.getUsedBits();
    }
private:
    const BucketState* _state;
    Priority _priority;
    uint16_t _node;
};

}

document::BucketId
OperationTargetResolver::bestBucketToCreate(
        const document::BucketId& target) const
{
    return _manager.getAppropriateBucket(target);
}

document::BucketId
OperationTargetResolver::getHighestSplitBucketAcrossNodes(
        const document::BucketId& target,
        const std::vector<BucketDatabase::Entry>& entries) const
{
    document::BucketId highest;
    if (entries.empty()) {
        highest = bestBucketToCreate(target);
    }
    for (uint32_t i = 0; i < entries.size(); ++i) {
        const BucketDatabase::Entry& entry(entries[i]);
        if (entry.getBucketId().getUsedBits() > highest.getUsedBits()) {
            highest = entry.getBucketId();
        }
    }
    return highest;
}

namespace {

NodePriority
findBestExistingCopyOnNode(uint16_t node,
                           const std::vector<BucketState>& states,
                           const std::vector<uint16_t>& idealNodes)
{
    NodePriority best(NULL, NONE_FOUND, node);

    for (size_t j = 0; j < states.size(); ++j) {
        if (states[j].copy.trusted()) {
            // We always prefer to send to trusted copies.
            if (best.worseThan(TRUSTED)
                || best.lessSplitThan(states[j].bid))
            {
                best.reset(&states[j], TRUSTED);
            }
        } else if (std::find(idealNodes.begin(), idealNodes.end(), node)
                   != idealNodes.end())
        {
            // Node is in ideal state for the highest split bucket.
            if (best.worseThan(IDEAL_STATE_NOT_TRUSTED)
                || (best.equalTo(IDEAL_STATE_NOT_TRUSTED)
                    && best.lessSplitThan(states[j].bid)))
            {
                best.reset(&states[j], IDEAL_STATE_NOT_TRUSTED);
            }
        } else {
            // Not trusted or in ideal state for highest split; just add so
            // we have a "best effort" bucket on this node at all.
            if (best.worseThan(NOT_TRUSTED_OR_IDEAL)
                || (best.equalTo(NOT_TRUSTED_OR_IDEAL)
                    && best.lessSplitThan(states[j].bid)))
            {
                best.reset(&states[j], NOT_TRUSTED_OR_IDEAL);
            }
        }
    }
    return best;
}

}

void
OperationTargetResolver::getTargets(const BucketId& bid,
                                    std::vector<BucketDatabase::Entry>& entries,
                                    std::vector<OperationTarget>& sendToExisting,
                                    std::vector<OperationTarget>& createNew)
{
    entries.clear();
    _manager.getBucketDatabase().getParents(bid, entries);

    /*
     * 1. Find all buckets on all nodes and the highest split bucket.
     * 2. Add buckets on nodes where the bucket is either trusted (may or may
     *    not be the highest split on the node) OR if the node is in the
     *    ideal state for the highest split bucket.
     * 3. If redundancy is still not satisfied, create new buckets according to
     *    ideal state.
     */

    typedef std::map<uint16_t, std::vector<BucketState> > NodeBucketMap;
    NodeBucketMap bucketsPerNode;
    for (size_t i = 0; i < entries.size(); ++i) {
        const BucketDatabase::Entry& entry(entries[i]);
        const BucketInfo& info(entry.getBucketInfo());

        for (uint32_t j = 0; j < info.getNodeCount(); ++j) {
            const BucketCopy& copy(info.getNodeRef(j));
            bucketsPerNode[copy.getNode()].push_back(
                    BucketState(entry.getBucketId(), copy));
        }
    }

    document::BucketId highestSplitIdAcrossNodes(
            getHighestSplitBucketAcrossNodes(bid, entries));

    std::vector<uint16_t> idealNodes(
            _manager.getDistribution().getIdealStorageNodes(
                    _manager.getClusterState(), highestSplitIdAcrossNodes, "ui"));

    std::priority_queue<NodePriority> candidates;

    // Create prioritized list of node+bucket pairs.
    for (NodeBucketMap::iterator it(bucketsPerNode.begin()),
             e(bucketsPerNode.end()); it != e; ++it)
    {
        const uint16_t node(it->first);
        const std::vector<BucketState>& states(it->second);

        NodePriority best(findBestExistingCopyOnNode(node, states, idealNodes));
        if (best.isValid()) {
            candidates.push(best);
        }
    }
    vespalib::hash_set<uint16_t> existingNodes(candidates.size() * 2);
    while (!candidates.empty()
           && sendToExisting.size() < idealNodes.size())
    {
        const NodePriority& np(candidates.top());
        sendToExisting.push_back(OperationTarget(np.getState()->bid, np.getNode()));
        existingNodes.insert(np.getNode());
        candidates.pop();
    }

    // If the wanted redundancy has not been satisfied by the existing copies,
    // we have to create additional ones. For this, we create the highest split
    // bucket on any ideal nodes that don't already have a copy.
    for (size_t i = 0; i < idealNodes.size(); ++i) {
        if (sendToExisting.size() + createNew.size() >= idealNodes.size()) {
            break;
        }
        const uint16_t ideal = idealNodes[i];
        if (existingNodes.find(ideal) != existingNodes.end()) {
            continue;
        }
        createNew.push_back(OperationTarget(highestSplitIdAcrossNodes, ideal));
    }
    assert(sendToExisting.size() + createNew.size() == idealNodes.size());
    // Sort based on bucket and nodes to make operation ordering consistent
    std::sort(sendToExisting.begin(), sendToExisting.end());
    std::sort(createNew.begin(), createNew.end());
}

}
}

