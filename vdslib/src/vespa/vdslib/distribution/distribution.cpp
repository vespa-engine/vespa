// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distribution.h"
#include "distribution_config_util.h"
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/vespalib/util/bobhash.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/config/print/asciiconfigwriter.h>
#include <vespa/config/print/asciiconfigreader.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/config-stor-distribution.h>
#include <cmath>
#include <cassert>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".vdslib.distribution");

namespace storage::lib {

VESPA_IMPLEMENT_EXCEPTION(NoDistributorsAvailableException, vespalib::Exception);
VESPA_IMPLEMENT_EXCEPTION(TooFewBucketBitsInUseException, vespalib::Exception);

Distribution::Distribution()
    : _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _readyCopies(0),
      _global(false),
      _activePerGroup(false),
      _ensurePrimaryPersisted(true),
      _relative_node_order_scoring(false)
{
    auto config(getDefaultDistributionConfig(0, 0));
    vespalib::asciistream ost;
    config::AsciiConfigWriter writer(ost);
    writer.write(config.get());
    _serialized = ost.view();
    configure(config.get());
}

// Fields are set through configure() in the constructor body
Distribution::Distribution(const Distribution& d)
    : _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _readyCopies(0),
      _global(d.is_global()),
      _activePerGroup(false),
      _ensurePrimaryPersisted(true),
      _relative_node_order_scoring(false),
      _serialized(d._serialized)
{
    vespalib::asciistream ist(_serialized);
    config::AsciiConfigReader<vespa::config::content::StorDistributionConfig> reader(ist);
    configure(*reader.read());
}

Distribution::ConfigWrapper::ConfigWrapper(std::unique_ptr<DistributionConfig> cfg) noexcept
    : _cfg(std::move(cfg))
{ }

Distribution::ConfigWrapper::~ConfigWrapper() = default;

std::unique_ptr<Distribution::DistributionConfig>
Distribution::ConfigWrapper::steal() noexcept {
    return std::move(_cfg);
}

Distribution::Distribution(const ConfigWrapper& config)
    : Distribution(config.get())
{
}

Distribution::Distribution(const vespa::config::content::StorDistributionConfig& config)
    : Distribution(config, false)
{
}

Distribution::Distribution(const vespa::config::content::StorDistributionConfig& config, bool is_global)
    : _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _readyCopies(0),
      _global(is_global),
      _activePerGroup(false),
      _ensurePrimaryPersisted(true),
      _relative_node_order_scoring(false)
{
    vespalib::asciistream ost;
    config::AsciiConfigWriter writer(ost);
    writer.write(config);
    _serialized = ost.view();
    configure(config);
}

Distribution::Distribution(const std::string& serialized)
    : _nodeGraph(),
      _node2Group(),
      _redundancy(),
      _initialRedundancy(0),
      _readyCopies(0),
      _global(false),
      _activePerGroup(false),
      _ensurePrimaryPersisted(true),
      _relative_node_order_scoring(false),
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
    using DistrConfig = vespa::config::content::StorDistributionConfig;
    using ConfigGroup = DistrConfig::Group;
    std::unique_ptr<Group> nodeGraph;
    std::vector<const Group *> node2Group;
    for (uint32_t i=0, n=config.group.size(); i<n; ++i) {
        const ConfigGroup& cg(config.group[i]);
        std::vector<uint16_t> path;
        if (nodeGraph) {
            path = DistributionConfigUtil::getGroupPath(cg.index);
        }
        bool isLeafGroup = ! cg.nodes.empty();
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
            // We want to normalize node order by distribution keys when we score
            // using distribution keys, but _not_ when scoring based on relative
            // configured index (as that would mess up this ordering).
            group->setNodes(nodes, !config.relativeNodeOrderScoring);
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
            "have a root group at minimum:\n" + _serialized, VESPA_STRLOC);
    }
    nodeGraph->finalize();
    _nodeGraph = std::move(nodeGraph);
    _node2Group = std::move(node2Group);
    _redundancy = config.redundancy;
    _initialRedundancy = config.initialRedundancy;
    _ensurePrimaryPersisted = config.ensurePrimaryPersisted;
    _readyCopies = config.readyCopies;
    _activePerGroup = config.activePerLeafGroup;
    _relative_node_order_scoring = config.relativeNodeOrderScoring;
    if (_global) {
        // Top-level `_redundancy` is used for flat topologies, in which case global
        // distribution is trivial.
        _redundancy = _nodeGraph->descendent_node_count();
        // All replicas shall be indexed the data for globally distributed documents.
        _readyCopies = _redundancy;
        _initialRedundancy = 0;
        _ensurePrimaryPersisted = true;
        _activePerGroup = true;
    }
}

uint32_t
Distribution::getGroupSeed(const document::BucketId& bucket, const ClusterState& clusterState, const Group& group) const
{
    uint32_t seed(static_cast<uint32_t>(bucket.getRawId())
                  & distribution_bit_mask(clusterState.getDistributionBitCount()));
    seed ^= group.getDistributionHash();
    return seed;
}

uint32_t
Distribution::getDistributorSeed(const document::BucketId& bucket, const ClusterState& state) const
{
    uint32_t seed(static_cast<uint32_t>(bucket.getRawId())
                  & distribution_bit_mask(state.getDistributionBitCount()));
    return seed;
}

uint32_t
Distribution::getStorageSeed(const document::BucketId& bucket, const ClusterState& state) const
{
    uint32_t seed(static_cast<uint32_t>(bucket.getRawId())
                  & distribution_bit_mask(state.getDistributionBitCount()));

    if (bucket.getUsedBits() > 33) {
        int usedBits = bucket.getUsedBits() - 1;
        seed ^= (distribution_bit_mask(usedBits - 32)
                 & (bucket.getRawId() >> 32)) << 6;
    }
    return seed;
}

void
Distribution::print(std::ostream& out, bool, const std::string&) const {
    out << serialized();
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
Distribution::getIdealGroups(const document::BucketId& bucket, const ClusterState& clusterState, const Group& parent,
                             uint16_t redundancy, std::vector<ResultGroup>& results) const
{
    if (parent.isLeafGroup()) {
        results.emplace_back(parent, redundancy);
        return;
    }
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
    if (!_global) [[likely]] {
        assert(parent.redundancy_value_within_bounds(redundancy));
        const Group::Distribution& redundancyArray = parent.getDistribution(redundancy);
        if (tmpResults.size() > redundancyArray.size()) {
            tmpResults.resize(redundancyArray.size());
        }
        for (uint32_t i=0, n=tmpResults.size(); i<n; ++i) {
            ScoredGroup& group(tmpResults[i]);
            getIdealGroups(bucket, clusterState, *group._group, redundancyArray[i], results);
        }
    } else {
        for (ScoredGroup& group : tmpResults) {
            getIdealGroups(bucket, clusterState, *group._group, group._group->descendent_node_count(), results);
        }
    }
}

const Group*
Distribution::getIdealDistributorGroup(const document::BucketId& bucket, const ClusterState& clusterState, const Group& parent) const
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
        while (subGroup.first < currentIndex++) {
            random.nextDouble();
        }
        double score = random.nextDouble();
        if (subGroup.second->getCapacity() != 1) {
            // Capacity shouldn't possibly be 0.
            // Verified in Group::setCapacity()
            score = std::pow(score, 1.0 / subGroup.second->getCapacity().getValue());
        }
        if (score > result._score) {
            if (!allDistributorsDown(*subGroup.second, clusterState)) {
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
        for (uint16_t node : g.getNodes()) {
            const NodeState& ns(cs.getNodeState(Node(NodeType::DISTRIBUTOR, node)));
            if (ns.getState().oneOf("ui")) {
                return false;
            }
        }
    } else {
        for (const auto & subGroup : g.getSubGroups()) {
            if (!allDistributorsDown(*subGroup.second, cs)) {
                return false;
            }
        }
    }
    return true;
}

void
Distribution::getIdealNodes(const NodeType& nodeType, const ClusterState& clusterState, const document::BucketId& bucket,
                            std::vector<uint16_t>& resultNodes, const char* upStates, uint16_t redundancy) const
{
    if (redundancy == DEFAULT_REDUNDANCY) {
        redundancy = _redundancy;
    }
    resultNodes.clear();
    if (redundancy == 0) {
        return;
    }
    // If bucket is split less than distribution bit, we cannot distribute
    // it. Different nodes own various parts of the bucket.
    if (bucket.getUsedBits() < clusterState.getDistributionBitCount()) {
        vespalib::asciistream ost;
        ost << "Cannot get ideal state for bucket " << bucket << " using "
            << bucket.getUsedBits() << " bits when cluster uses "
            << clusterState.getDistributionBitCount() << " distribution bits.";
        throw TooFewBucketBitsInUseException(ost.view(), VESPA_STRLOC);
    }
    // Find what hierarchical groups we should have copies in
    std::vector<ResultGroup> group_distribution;
    uint32_t seed;
    if (nodeType == NodeType::STORAGE) {
        seed = getStorageSeed(bucket, clusterState);
        getIdealGroups(bucket, clusterState, *_nodeGraph, redundancy, group_distribution);
    } else {
        seed = getDistributorSeed(bucket, clusterState);
        const Group* group(getIdealDistributorGroup(bucket, clusterState, *_nodeGraph));
        if (group == nullptr) {
            vespalib::asciistream ss;
            ss << "There is no legal distributor target in state with version " << clusterState.getVersion();
            throw NoDistributorsAvailableException(ss.view(), VESPA_STRLOC);
        }
        group_distribution.emplace_back(*group, 1);
    }
    RandomGen random(seed);
    uint32_t randomIndex = 0;
    std::vector<ScoredNode> tmpResults;
    for (const auto& group : group_distribution) {
        uint16_t groupRedundancy(group._redundancy);
        const std::vector<uint16_t>& nodes(group._group->getNodes());
        // Create temporary place to hold results.
        // Stuff in redundancy fake entries to
        // avoid needing to check size during iteration.
        tmpResults.reserve(groupRedundancy);
        tmpResults.clear();
        tmpResults.resize(groupRedundancy);
        uint16_t scoring_index = 0;
        for (const uint16_t node : nodes) {
            // Verify that the node is legal target before starting to grab
            // random number. Helps worst case of having to start new random
            // seed if the node that is out of order is illegal anyways.
            const NodeState& nodeState(clusterState.getNodeState(Node(nodeType, node)));
            if (!nodeState.getState().oneOf(upStates)) {
                // For pseudo row-column, we treat Retired nodes as if they do not exist in
                // the configuration. Since Retired is meant for removing nodes, this is
                // expected to be the end state either way, so unless we do this up front,
                // there will be two rounds of data movement.
                // This has the downside of "shifting down" the assigned nodes for a given
                // ideal state score by one, which causes mass data redistribution for all
                // nodes configured _after_ the Retired node. On the upside, if retirement
                // is done via reconfiguration, the config edge can atomically retire one
                // node and introduce a new node configured right after it. This node will
                // then effectively take the old node's place, receiving all its documents
                // without any other nodes receiving new data.
                // This has no effect for non-pseudo-row-column, as we always set the
                // scoring index from the node's distribution key below.
                if (nodeState.getState() != State::RETIRED) [[likely]] {
                    ++scoring_index;
                }
                continue;
            }
            if (!_relative_node_order_scoring) [[likely]] {
                scoring_index = node;
            }
            // Get the score from the random number generator. Make sure we
            // pick correct random number. Optimize for the case where we
            // pick in rising order.
            if (scoring_index != randomIndex) {
                if (scoring_index < randomIndex) {
                    random.setSeed(seed);
                    randomIndex = 0;
                }
                for (uint32_t k=randomIndex, o=scoring_index; k<o; ++k) {
                    random.nextDouble();
                }
                randomIndex = scoring_index;
            }
            double score = random.nextDouble();
            ++randomIndex;
            ++scoring_index;
            if (nodeState.getCapacity() != vespalib::Double(1.0)) {
                score = std::pow(score, 1.0 / nodeState.getCapacity().getValue());
            }
            if (score > tmpResults.back()._score) {
                insertOrdered(tmpResults, ScoredNode(score, node));
            }
        }
        trimResult(tmpResults, groupRedundancy);
        resultNodes.reserve(resultNodes.size() + tmpResults.size());
        for (const auto& scored : tmpResults) {
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
Distribution::getIdealStorageNodes(const ClusterState& state, const document::BucketId& bucket, const char* upStates) const
{
    std::vector<uint16_t> nodes;
    getIdealNodes(NodeType::STORAGE, state, bucket, nodes, upStates);
    return nodes;
}

uint16_t
Distribution::getIdealDistributorNode(const ClusterState& state, const document::BucketId& bucket, const char* upStates) const
{
    std::vector<uint16_t> nodes;
    getIdealNodes(NodeType::DISTRIBUTOR, state, bucket, nodes, upStates);
    assert(nodes.size() <= 1);
    if (nodes.empty()) {
        vespalib::asciistream ss;
        ss << "There is no legal distributor target in state with version " << state.getVersion();
        throw NoDistributorsAvailableException(ss.view(), VESPA_STRLOC);
    }
    return nodes[0];
}

std::vector<Distribution::IndexList>
Distribution::splitNodesIntoLeafGroups(std::span<const uint16_t> nodeList) const
{
    vespalib::hash_map<uint16_t, IndexList> nodes(nodeList.size());
    for (auto node : nodeList) {
        const Group* group((node < _node2Group.size()) ? _node2Group[node] : nullptr);
        if (group == nullptr) {
            LOGBP(warning, "Node %u is not assigned to a group. Should not happen?", node);
        } else {
            assert(group->isLeafGroup());
            nodes[group->getIndex()].push_back(node);
        }
    }
    std::vector<uint16_t> sorted;
    sorted.reserve(nodes.size());
    for (const auto & entry : nodes) {
        sorted.push_back(entry.first);
    }
    std::sort(sorted.begin(), sorted.end());
    std::vector<IndexList> result;
    result.reserve(nodes.size());
    for (uint16_t groupId : sorted) {
        result.emplace_back(std::move(nodes.find(groupId)->second));
    }
    return result;
}

}
