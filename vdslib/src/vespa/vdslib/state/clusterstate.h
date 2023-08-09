// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::ClusterState
 * @ingroup state
 *
 * @brief Object to represent a system state
 */

#pragma once

#include "node.h"
#include "nodestate.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <array>

namespace storage::lib {

class Distribution;
class Group;
struct NodeData;
struct SeparatorPrinter;

class ClusterState : public document::Printable {
public:
    using NodeStatePair = std::pair<Node, NodeState>;
    using NodeMap = vespalib::hash_map<Node, NodeState>;
    using NodeCounts = std::array<uint16_t, 2>;
    using CSP = std::shared_ptr<const ClusterState>;
    using SP = std::shared_ptr<ClusterState>;
    using UP = std::unique_ptr<ClusterState>;

    ClusterState();
    ClusterState(const ClusterState&);
    // FIXME make ClusterState parsing not require null termination of string,
    // then move to vespalib::stringref
    explicit ClusterState(const vespalib::string& serialized);
    ClusterState& operator=(const ClusterState& other) = delete;
    ~ClusterState();

    std::string getTextualDifference(const ClusterState& other) const;
    void serialize(vespalib::asciistream & out) const;

    bool operator==(const ClusterState& other) const noexcept;
    bool operator!=(const ClusterState& other) const noexcept;

    uint32_t getVersion() const noexcept { return _version; }
    /**
     * Returns the smallest number above the highest node index found of the
     * given type that is not down.
     */
    uint16_t getNodeCount(const NodeType& type) const noexcept { return _nodeCount[type]; }
    uint16_t getDistributionBitCount() const noexcept { return _distributionBits; }
    const State& getClusterState() const noexcept { return *_clusterState; }
    const NodeState& getNodeState(const Node& node) const;

    void setVersion(uint32_t version) noexcept { _version = version; }
    void setClusterState(const State& state);
    void setNodeState(const Node& node, const NodeState& state);
    void setDistributionBitCount(uint16_t count) noexcept { _distributionBits = count; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void printStateGroupwise(std::ostream& out, const Distribution&, bool verbose, const std::string& indent) const;

private:
    // Preconditions: `key` and `value` MUST point into null-terminated strings.
    bool parse(vespalib::stringref key, vespalib::stringref value, NodeData & nodeData);
    // Preconditions: `key` and `value` MUST point into null-terminated strings.
    bool parseSorD(vespalib::stringref key, vespalib::stringref value, NodeData & nodeData);
    void removeExtraElements();
    void removeExtraElements(const NodeType& type);
    void printStateGroupwise(std::ostream& out, const Group&, bool verbose, const std::string& indent, bool rootGroup) const;
    void getTextualDifference(std::ostringstream& builder, const NodeType& type, const ClusterState& other) const;
    size_t printStateGroupwise(std::ostream& out, const Group&, bool verbose, const std::string& indent, const NodeType& type) const;
    void serialize_nodes(vespalib::asciistream & out, SeparatorPrinter & sep, const NodeType & nodeType,
                         const std::vector<NodeStatePair> & nodeStates) const;
    uint32_t           _version;
    NodeCounts         _nodeCount;
    const State*       _clusterState;
    NodeMap            _nodeStates;
    vespalib::string   _description;
    uint16_t           _distributionBits;
};

}
