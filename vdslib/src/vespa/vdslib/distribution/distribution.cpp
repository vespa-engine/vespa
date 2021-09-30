// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distribution.h"
#include "distribution_config_util.h"
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/vespalib/util/bobhash.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/config/config.h>
#include <vespa/config/print/asciiconfigwriter.h>
#include <vespa/config/print/asciiconfigreader.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/config-stor-distribution.h>
#include <list>
#include <algorithm>
#include <cmath>
#include <cassert>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".vdslib.distribution");

namespace storage::lib {

namespace {
    std::vector<uint32_t> getDistributionBitMasks() {
        std::vector<uint32_t> masks;
        masks.resize(32 + 1);
        uint32_t mask = 0;
        for (uint32_t i=0; i<=32; ++i) {
            masks[i] = mask;
            mask = (mask << 1) | 1;
        }
        return masks;
    }
}

VESPA_IMPLEMENT_EXCEPTION(NoDistributorsAvailableException, vespalib::Exception);
VESPA_IMPLEMENT_EXCEPTION(TooFewBucketBitsInUseException, vespalib::Exception);

Distribution::Distribution()
    : _distributionBitMasks(getDistributionBitMasks()),
      _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _ensurePrimaryPersisted(true)
{
    auto config(getDefaultDistributionConfig(0, 0));
    vespalib::asciistream ost;
    config::AsciiConfigWriter writer(ost);
    writer.write(config.get());
    _serialized = ost.str();
    configure(config.get());
}

Distribution::Distribution(const Distribution& d)
    : _distributionBitMasks(getDistributionBitMasks()),
      _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _ensurePrimaryPersisted(true),
      _serialized(d._serialized)
{
    vespalib::asciistream ist(_serialized);
    config::AsciiConfigReader<vespa::config::content::StorDistributionConfig> reader(ist);
    configure(*reader.read());
}

Distribution::ConfigWrapper::ConfigWrapper(std::unique_ptr<DistributionConfig> cfg) :
    _cfg(std::move(cfg))
{ }

Distribution::ConfigWrapper::~ConfigWrapper() = default;

Distribution::Distribution(const ConfigWrapper & config) :
    Distribution(config.get())
{ }

Distribution::Distribution(const vespa::config::content::StorDistributionConfig & config)
    : _distributionBitMasks(getDistributionBitMasks()),
      _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _ensurePrimaryPersisted(true)
{
    vespalib::asciistream ost;
    config::AsciiConfigWriter writer(ost);
    writer.write(config);
    _serialized = ost.str();
    configure(config);
}

Distribution::Distribution(const vespalib::string& serialized)
    : _distributionBitMasks(getDistributionBitMasks()),
      _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _ensurePrimaryPersisted(true),
      _serialized(serialized)
{
    vespalib::asciistream ist(_serialized);
    config::AsciiConfigReader<vespa::config::content::StorDistributionConfig> reader(ist);
    configure(*reader.read());
}

Distribution::~Distribution() = default;

void
Distribution::configure(const vespa::config::content::StorDistributionConfig& config)
{
    typedef vespa::config::content::StorDistributionConfig::Group ConfigGroup;
    std::unique_ptr<Group> nodeGraph;
    std::vector<const Group *> node2Group;
    for (uint32_t i=0, n=config.group.size(); i<n; ++i) {
        const ConfigGroup& cg(config.group[i]);
        std::vector<uint16_t> path;
        if (nodeGraph) {
            path = DistributionConfigUtil::getGroupPath(cg.index);
        }
        bool isLeafGroup = (cg.nodes.size() > 0);
        uint16_t index = (path.empty() ? 0 : path.back());
        std::unique_ptr<Group> group = (isLeafGroup)
                ? std::make_unique<Group>(index, cg.name)
                : std::make_unique<Group>(index, cg.name, Group::Distribution(cg.partitions), config.redundancy);
        group->setCapacity(cg.capacity);
        if (isLeafGroup) {
            std::vector<uint16_t> nodes(cg.nodes.size());
            for (uint32_t j=0, m=nodes.size(); j<m; ++j) {
                uint16_t nodeIndex = cg.nodes[j].index;
                nodes[j] = nodeIndex;
                if (node2Group.size() <= nodeIndex) {
                    node2Group.resize(nodeIndex + 1);
                }
                node2Group[nodeIndex] = group.get();
            }
            group->setNodes(nodes);
        }
        if (path.empty()) {
            nodeGraph = std::move(group);
        } else {
            assert(nodeGraph);
            Group* parent = nodeGraph.get();
            for (uint32_t j=0; j<path.size() - 1; ++j) {
                parent = parent->getSubGroups()[path[j]];
            }
            parent->addSubGroup(std::move(group));
        }
    }
    if ( ! nodeGraph) {
        throw vespalib::IllegalStateException(
            "Got config that didn't seem to specify even a root group. Must "
            "have a root group at minimum:\n"
            + _serialized, VESPA_STRLOC);
    }
    nodeGraph->calculateDistributionHashValues();
    _nodeGraph = std::move(nodeGraph);
    _node2Group = std::move(node2Group);
    _redundancy = config.redundancy;
    _initialRedundancy = config.initialRedundancy;
    _ensurePrimaryPersisted = config.ensurePrimaryPersisted;
    _readyCopies = config.readyCopies;
    _activePerGroup = config.activePerLeafGroup;
    _distributorAutoOwnershipTransferOnWholeGroupDown
            = config.distributorAutoOwnershipTransferOnWholeGroupDown;
}

uint32_t
Distribution::getGroupSeed(
        const document::BucketId& bucket, const ClusterState& clusterState,
        const Group& group) const
{
    uint32_t seed(static_cast<uint32_t>(bucket.getRawId())
            & _distributionBitMasks[clusterState.getDistributionBitCount()]);
    seed ^= group.getDistributionHash();
    return seed;
}

uint32_t
Distribution::getDistributorSeed(
        const document::BucketId& bucket, const ClusterState& state) const
{
    uint32_t seed(static_cast<uint32_t>(bucket.getRawId())
            & _distributionBitMasks[state.getDistributionBitCount()]);
    return seed;
}

uint32_t
Distribution::getStorageSeed(
        const document::BucketId& bucket, const ClusterState& state) const
{
    uint32_t seed(static_cast<uint32_t>(bucket.getRawId())
            & _distributionBitMasks[state.getDistributionBitCount()]);

    if (bucket.getUsedBits() > 33) {
        int usedBits = bucket.getUsedBits() - 1;
        seed ^= (_distributionBitMasks[usedBits - 32]
                 & (bucket.getRawId() >> 32)) << 6;
    }
    return seed;
}

void
Distribution::print(std::ostream& out, bool, const std::string&) const {
    out << serialize();
}

namespace {

    /** Used to record scored groups during ideal groups calculation. */
    struct ScoredGroup {
        double       _score;
        const Group* _group;

        ScoredGroup() noexcept : _score(0), _group(nullptr) { }
        ScoredGroup(double score, const Group* group) noexcept
            : _score(score), _group(group) { }

        bool operator<(const ScoredGroup& other) const noexcept {
            return (_score > other._score);
        }
    };

    /** Used to record scored nodes during ideal nodes calculation. */
    struct ScoredNode {
        double   _score;
        uint16_t _index;

        constexpr ScoredNode() noexcept : _score(0), _index(UINT16_MAX) {}
        constexpr ScoredNode(double score, uint16_t index) noexcept
            : _score(score), _index(index) {}

        constexpr bool operator<(const ScoredNode& other) const noexcept {
            return (_score < other._score);
        }
        constexpr bool valid() const noexcept {
            return (_index != UINT16_MAX);
        }
    };

    // Trim the input vector so that no trailing invalid entries remain and that
    // it has a maximum size of `redundancy`.
    void
    trimResult(std::vector<ScoredNode>& nodes, uint16_t redundancy) {
        while (!nodes.empty() && (!nodes.back().valid() || (nodes.size() > redundancy))) {
            nodes.pop_back();
        }
    }

    void
    insertOrdered(std::vector<ScoredNode> & tmpResults, ScoredNode && scoredNode) {
        tmpResults.pop_back();
        auto it = tmpResults.begin();
        for (; it != tmpResults.end(); ++it) {
            if (*it < scoredNode) {
                tmpResults.insert(it, scoredNode);
                return;
            }
        }
        tmpResults.emplace_back(scoredNode);
    }
}

void
Distribution::getIdealGroups(const document::BucketId& bucket,
                             const ClusterState& clusterState,
                             const Group& parent,
                             uint16_t redundancy,
                             std::vector<ResultGroup>& results) const
{
    if (parent.isLeafGroup()) {
        results.emplace_back(parent, redundancy);
        return;
    }
    const Group::Distribution& redundancyArray = parent.getDistribution(redundancy);
    uint32_t seed = getGroupSeed(bucket, clusterState, parent);
    RandomGen random(seed);
    uint32_t currentIndex = 0;
    const auto& subGroups = parent.getSubGroups();
    std::vector<ScoredGroup> tmpResults;
    tmpResults.reserve(subGroups.size());
    for (const auto& g : subGroups) {
        while (g.first < currentIndex++) {
            random.nextDouble();
        }
        double score = random.nextDouble();
        if (g.second->getCapacity() != 1) {
            // Capacity shouldn't possibly be 0.
            // Verified in Group::setCapacity()
            score = std::pow(score, 1.0 / g.second->getCapacity().getValue());
        }
        tmpResults.emplace_back(score, g.second);
    }
    std::sort(tmpResults.begin(), tmpResults.end());
    if (tmpResults.size() > redundancyArray.size()) {
        tmpResults.resize(redundancyArray.size());
    }
    for (uint32_t i=0, n=tmpResults.size(); i<n; ++i) {
        ScoredGroup& group(tmpResults[i]);
        // This should never happen. Config should verify that each group
        // has enough groups beneath them.
        assert(group._group != nullptr);
        getIdealGroups(bucket, clusterState, *group._group,
                       redundancyArray[i], results);
    }
}

const Group*
Distribution::getIdealDistributorGroup(const document::BucketId& bucket,
                                       const ClusterState& clusterState,
                                       const Group& parent) const
{
    if (parent.isLeafGroup()) {
        return &parent;
    }
    ScoredGroup result;
    uint32_t seed(getGroupSeed(bucket, clusterState, parent));
    RandomGen random(seed);
    uint32_t currentIndex = 0;
    const std::map<uint16_t, Group*>& subGroups(parent.getSubGroups());
    for (const auto & subGroup : subGroups) {
        while (subGroup.first < currentIndex++) random.nextDouble();
        double score = random.nextDouble();
        if (subGroup.second->getCapacity() != 1) {
            // Capacity shouldn't possibly be 0.
            // Verified in Group::setCapacity()
            score = std::pow(score, 1.0 / subGroup.second->getCapacity().getValue());
        }
        if (score > result._score) {
            if (!_distributorAutoOwnershipTransferOnWholeGroupDown
                || !allDistributorsDown(*subGroup.second, clusterState))
            {
                result = ScoredGroup(score, subGroup.second);
            }
        }
    }
    if (result._group == nullptr) {
        return nullptr;
    }
    return getIdealDistributorGroup(bucket, clusterState, *result._group);
}

bool
Distribution::allDistributorsDown(const Group& g, const ClusterState& cs)
{
    if (g.isLeafGroup()) {
        for (uint32_t i=0, n=g.getNodes().size(); i<n; ++i) {
            const NodeState& ns(cs.getNodeState(Node(NodeType::DISTRIBUTOR, g.getNodes()[i])));
            if (ns.getState().oneOf("ui")) return false;
        }
    } else {
        for (const auto & subGroup : g.getSubGroups()) {
            if (!allDistributorsDown(*subGroup.second, cs)) return false;
        }
    }
    return true;
}

void
Distribution::getIdealNodes(const NodeType& nodeType,
                            const ClusterState& clusterState,
                            const document::BucketId& bucket,
                            std::vector<uint16_t>& resultNodes,
                            const char* upStates,
                            uint16_t redundancy) const
{
    if (redundancy == DEFAULT_REDUNDANCY) redundancy = _redundancy;
    resultNodes.clear();
    if (redundancy == 0) return;

    // If bucket is split less than distribution bit, we cannot distribute
    // it. Different nodes own various parts of the bucket.
    if (bucket.getUsedBits() < clusterState.getDistributionBitCount()) {
        vespalib::asciistream ost;
        ost << "Cannot get ideal state for bucket " << bucket << " using "
            << bucket.getUsedBits() << " bits when cluster uses "
            << clusterState.getDistributionBitCount() << " distribution bits.";
        throw TooFewBucketBitsInUseException(ost.str(), VESPA_STRLOC);
    }
    // Find what hierarchical groups we should have copies in
    std::vector<ResultGroup> _groupDistribution;
    uint32_t seed;
    if (nodeType == NodeType::STORAGE) {
        seed = getStorageSeed(bucket, clusterState);
        getIdealGroups(bucket, clusterState, *_nodeGraph, redundancy, _groupDistribution);
    } else {
        seed = getDistributorSeed(bucket, clusterState);
        const Group* group(getIdealDistributorGroup(bucket, clusterState, *_nodeGraph));
        if (group == nullptr) {
            vespalib::asciistream ss;
            ss << "There is no legal distributor target in state with version "
               << clusterState.getVersion();
            throw NoDistributorsAvailableException(ss.str(), VESPA_STRLOC);
        }
        _groupDistribution.push_back(ResultGroup(*group, 1));
    }
    RandomGen random(seed);
    uint32_t randomIndex = 0;
    std::vector<ScoredNode> tmpResults;
    for (uint32_t i=0, n=_groupDistribution.size(); i<n; ++i) {
        uint16_t groupRedundancy(_groupDistribution[i]._redundancy);
        const std::vector<uint16_t>& nodes(_groupDistribution[i]._group->getNodes());
        // Create temporary place to hold results.
        // Stuff in redundancy fake entries to
        // avoid needing to check size during iteration.
        tmpResults.reserve(groupRedundancy);
        tmpResults.clear();
        tmpResults.resize(groupRedundancy);
        for (uint32_t j=0; j < nodes.size(); ++j) {
            // Verify that the node is legal target before starting to grab
            // random number. Helps worst case of having to start new random
            // seed if the node that is out of order is illegal anyways.
            const NodeState& nodeState(clusterState.getNodeState(Node(nodeType, nodes[j])));
            if (!nodeState.getState().oneOf(upStates)) continue;
            // Get the score from the random number generator. Make sure we
            // pick correct random number. Optimize for the case where we
            // pick in rising order.
            if (nodes[j] != randomIndex) {
                if (nodes[j] < randomIndex) {
                    random.setSeed(seed);
                    randomIndex = 0;
                }
                for (uint32_t k=randomIndex, o=nodes[j]; k<o; ++k) {
                    random.nextDouble();
                }
                randomIndex = nodes[j];
            }
            double score = random.nextDouble();
            ++randomIndex;
            if (nodeState.getCapacity() != vespalib::Double(1.0)) {
                score = std::pow(score, 1.0 / nodeState.getCapacity().getValue());
            }
            if (score > tmpResults.back()._score) {
                insertOrdered(tmpResults, ScoredNode(score, nodes[j]));
            }
        }
        trimResult(tmpResults, groupRedundancy);
        resultNodes.reserve(resultNodes.size() + tmpResults.size());
        for (const auto & scored : tmpResults) {
            resultNodes.push_back(scored._index);
        }
    }
}

Distribution::ConfigWrapper
Distribution::getDefaultDistributionConfig(uint16_t redundancy, uint16_t nodeCount)
{
    auto config = std::make_unique<vespa::config::content::StorDistributionConfigBuilder>();
    config->redundancy = redundancy;
    config->group.resize(1);
    config->group[0].index = "invalid";
    config->group[0].name = "invalid";
    config->group[0].partitions = "*";
    config->group[0].nodes.resize(nodeCount);
    for (uint16_t i=0; i<nodeCount; ++i) {
        config->group[0].nodes[i].index = i;
    }
    return ConfigWrapper(std::move(config));
}

std::vector<uint16_t>
Distribution::getIdealStorageNodes(
            const ClusterState& state, const document::BucketId& bucket,
            const char* upStates) const
{
    std::vector<uint16_t> nodes;
    getIdealNodes(NodeType::STORAGE, state, bucket, nodes, upStates);
    return nodes;
}

uint16_t
Distribution::getIdealDistributorNode(
            const ClusterState& state,
            const document::BucketId& bucket,
            const char* upStates) const
{
    std::vector<uint16_t> nodes;
    getIdealNodes(NodeType::DISTRIBUTOR, state, bucket, nodes, upStates);
    assert(nodes.size() <= 1);
    if (nodes.empty()) {
        vespalib::asciistream ss;
        ss << "There is no legal distributor target in state with version "
           << state.getVersion();
        throw NoDistributorsAvailableException(ss.str(), VESPA_STRLOC);
    }
    return nodes[0];
}

std::vector<Distribution::IndexList>
Distribution::splitNodesIntoLeafGroups(IndexList nodeList) const
{
    std::vector<IndexList> result;
    std::map<uint16_t, IndexList> nodes;
    for (auto node : nodeList) {
        const Group* group((node < _node2Group.size()) ? _node2Group[node] : nullptr);
        if (group == nullptr) {
            LOGBP(warning, "Node %u is not assigned to a group. "
                           "Should not happen?", node);
        } else {
            assert(group->isLeafGroup());
            nodes[group->getIndex()].push_back(node);
        }
    }
    result.reserve(nodes.size());
    for (auto & node : nodes) {
        result.emplace_back(std::move(node.second));
    }
    return result;
}

}
