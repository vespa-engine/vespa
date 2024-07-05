// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "clusterstate.h"
#include "globals.h"

#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <sstream>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".vdslib.state.cluster");

using vespalib::IllegalArgumentException;

using storage::lib::clusterstate::_G_defaultSDState;
using storage::lib::clusterstate::_G_defaultDDState;
using storage::lib::clusterstate::_G_defaultSUState;
using storage::lib::clusterstate::_G_defaultDUState;

namespace storage::lib {

ClusterState::ClusterState()
    : Printable(),
      _version(0),
      _nodeCount(),
      _clusterState(&State::DOWN),
      _nodeStates(),
      _description(),
      _distributionBits(16)
{ }

ClusterState::ClusterState(const ClusterState& other) = default;
ClusterState::~ClusterState() = default;

struct NodeData {
    bool empty;
    Node node;
    vespalib::asciistream ost;

    NodeData() : empty(true), node(NodeType::STORAGE, 0), ost() {}

    void addTo(ClusterState::NodeMap & nodeStates, ClusterState::NodeCounts & nodeCount) {
        if (!empty) {
            NodeState state(ost.str(), &node.getType());
            if ((state != NodeState(node.getType(), State::UP)) || (state.getDescription().size() > 0)) {
                nodeStates.insert(std::make_pair(node, state));
            }
            if (nodeCount[node.getType()] <= node.getIndex()) {
                nodeCount[node.getType()] = node.getIndex() + 1;
            }
            empty = true;
            ost.clear();
        }
    }
};

ClusterState::ClusterState(const vespalib::string& serialized)
    : Printable(),
      _version(0),
      _nodeCount(),
      _clusterState(&State::UP),
      _nodeStates(),
      _description(),
      _distributionBits(16)
{
    vespalib::StringTokenizer st(serialized, " \t\f\r\n");
    st.removeEmptyTokens();
    NodeData nodeData;
    vespalib::string lastAbsolutePath;

    for (const auto & token : st) {
        vespalib::string::size_type index = token.find(':');
        if (index == vespalib::string::npos) {
            throw IllegalArgumentException("Token " + token + " does not contain ':': " + serialized, VESPA_STRLOC);
        }
        vespalib::string key = token.substr(0, index);
        std::string_view value = token.substr(index + 1);
        if (key.size() > 0 && key[0] == '.') {
            if (lastAbsolutePath == "") {
                throw IllegalArgumentException("The first path in system state string needs to be absolute", VESPA_STRLOC);
            }
            key = lastAbsolutePath + key;
        } else {
            lastAbsolutePath = key;
        }
        
        if (key.empty() || ! parse(key, value, nodeData) ) {
            LOG(debug, "Unknown key %s in systemstate. Ignoring it, assuming it's "
                       "a new feature from a newer version than ourself: %s",
                       vespalib::string(key).c_str(), serialized.c_str());
        }
    }
    nodeData.addTo(_nodeStates, _nodeCount);
    removeExtraElements();
}

bool
ClusterState::parse(std::string_view key, std::string_view value, NodeData & nodeData) {
    switch (key[0]) {
    case 'c':
        if (key == "cluster") {
            setClusterState(State::get(value));
            return true;
        }
        break;
    case 'b':
        if (key == "bits") {
            uint32_t numBits = atoi(value.data());
            assert(numBits <= 64);
            _distributionBits = numBits;
            return true;
        }
        break;
    case 'v':
        if (key == "version") {
            _version = atoi(value.data());
            return true;
        }
        break;
    case 'm':
        if (key.size() == 1) {
            _description = document::StringUtil::unescape(value);
            return true;
        }
        break;
    case 'd':
    case 's':
        return parseSorD(key, value, nodeData);
    default:
        break;
    }
    return false;
}

bool
ClusterState::parseSorD(std::string_view key, std::string_view value, NodeData & nodeData) {
    const NodeType* nodeType = nullptr;
    vespalib::string::size_type dot = key.find('.');
    std::string_view type(dot == vespalib::string::npos
                             ? key : key.substr(0, dot));
    if (type == "storage") {
        nodeType = &NodeType::STORAGE;
    } else if (type == "distributor") {
        nodeType = &NodeType::DISTRIBUTOR;
    }
    if (nodeType == nullptr) return false;
    if (dot == vespalib::string::npos) { // Entry that set node counts
        uint16_t nodeCount = atoi(value.data());

        if (nodeCount > _nodeCount[*nodeType] ) {
            _nodeCount[*nodeType] = nodeCount;
        }
        return true;
    }
    vespalib::string::size_type dot2 = key.find('.', dot + 1);
    Node node(*nodeType, (dot2 == vespalib::string::npos)
                         ? atoi(key.substr(dot + 1).data())
                         : atoi(key.substr(dot + 1, dot2 - dot - 1).data()));

    if (node.getIndex() >= _nodeCount[*nodeType]) {
        vespalib::asciistream ost;
        ost << "Cannot index " << *nodeType << " node " << node.getIndex() << " of " << _nodeCount[*nodeType];
        throw IllegalArgumentException( ost.str(), VESPA_STRLOC);
    }
    if (nodeData.node != node) {
        nodeData.addTo(_nodeStates, _nodeCount);
    }
    if (dot2 == vespalib::string::npos) {
        return false; // No default key for nodes.
    } else {
        nodeData.ost << " " << key.substr(dot2 + 1) << ':' << value;
    }
    nodeData.node = node;
    nodeData.empty = false;
    return true;
}

struct SeparatorPrinter {
    bool first;
    SeparatorPrinter() : first(true) {}
    const char * toString() {
        if (first) {
            first = false;
            return "";
        }
        return " ";
    }
};

namespace {

void
serialize_node(vespalib::asciistream & out, const Node & node, const NodeState & state) {
    vespalib::asciistream prefix;
    prefix << "." << node.getIndex() << ".";
    vespalib::asciistream ost;
    state.serialize(ost, prefix.str(), false);
    std::string_view content = ost.str();
    if ( !content.empty()) {
        out << " " << content;
    }
}

}

void
ClusterState::serialize_nodes(vespalib::asciistream & out, SeparatorPrinter & sep, const NodeType & nodeType,
                              const std::vector<NodeStatePair> & nodeStates) const
{
    uint16_t nodeCount = getNodeCount(nodeType);
    if (nodeCount > 0) {
        out << sep.toString() << nodeType.serialize() << ":" << nodeCount;
        for (const auto & entry : nodeStates) {
            if (entry.first.getType() == nodeType) {
                serialize_node(out, entry.first, entry.second);
            }
        }
    }
}

void
ClusterState::serialize(vespalib::asciistream & out) const
{
    SeparatorPrinter sep;
    if (_version != 0) {
        out << sep.toString() << "version:" << _version;
    }
    if (*_clusterState != State::UP) {
        out << sep.toString() << "cluster:" << _clusterState->serialize();
    }
    if (_distributionBits != 16) {
        out << sep.toString() << "bits:" << _distributionBits;
    }

    if ((getNodeCount(NodeType::DISTRIBUTOR) + getNodeCount(NodeType::STORAGE)) == 0u) return;

    std::vector<NodeStatePair> nodeStates(_nodeStates.cbegin(), _nodeStates.cend());
    std::sort(nodeStates.begin(), nodeStates.end(), [](const NodeStatePair &a, const NodeStatePair &b) { return a.first < b.first; });
    serialize_nodes(out, sep, NodeType::DISTRIBUTOR, nodeStates);
    serialize_nodes(out, sep, NodeType::STORAGE, nodeStates);
}

bool
ClusterState::operator==(const ClusterState& other) const noexcept
{
    return (_version == other._version &&
            *_clusterState == *other._clusterState &&
            _nodeStates == other._nodeStates &&
            _nodeCount == other._nodeCount &&
            _distributionBits == other._distributionBits);
}

bool
ClusterState::operator!=(const ClusterState& other) const noexcept
{
    return !(*this == other);
}

namespace {
    [[noreturn]] void throwUnknownType(const Node & node) __attribute__((noinline));
    void throwUnknownType(const Node & node) {
        throw vespalib::IllegalStateException("Unknown node type " + node.getType().toString(), VESPA_STRLOC);
    }
}

const NodeState&
ClusterState::getNodeState(const Node& node) const
{
    // If it actually has an entry in map, return that
    const auto it = _nodeStates.find(node);
    if (it != _nodeStates.end()) return it->second;

    // If beyond node count, the node is down.
    if (__builtin_expect(node.getIndex() >= _nodeCount[node.getType()], false)) {
        if (node.getType().getType() == NodeType::Type::STORAGE) {
            return _G_defaultSDState;
        } else if (node.getType().getType() == NodeType::Type::DISTRIBUTOR) {
            return _G_defaultDDState;
        }
    } else {
        // If not mentioned in map but within node count, the node is up
        if (node.getType().getType() == NodeType::Type::STORAGE) {
            return _G_defaultSUState;
        } else if (node.getType().getType() == NodeType::Type::DISTRIBUTOR) {
            return _G_defaultDUState;
        }
    }
    throwUnknownType(node);
}

void
ClusterState::setClusterState(const State& state)
{
    if (!state.validClusterState()) {
        throw vespalib::IllegalStateException(state.toString(true) + " is not a legal cluster state", VESPA_STRLOC);
    }
    _clusterState = &state;
}

void
ClusterState::setNodeState(const Node& node, const NodeState& state)
{
    state.verifySupportForNodeType(node.getType());
    if (node.getIndex() >= _nodeCount[node.getType()]) {
        for (uint32_t i = _nodeCount[node.getType()]; i < node.getIndex(); ++i) {
            _nodeStates.insert(std::make_pair(Node(node.getType(), i), NodeState(node.getType(), State::DOWN)));
        }
        _nodeCount[node.getType()] = node.getIndex() + 1;
    }
    if ((state == NodeState(node.getType(), State::UP)) && state.getDescription().empty()) {
        _nodeStates.erase(node);
    } else {
        _nodeStates.insert(std::make_pair(node, state));
    }

    removeExtraElements();
}

void
ClusterState::print(std::ostream& out, bool verbose, const std::string&) const
{
    (void) verbose;
    vespalib::asciistream tmp;
    serialize(tmp);
    out << tmp.str();
}

void
ClusterState::removeExtraElements()
{
    removeExtraElements(NodeType::STORAGE);
    removeExtraElements(NodeType::DISTRIBUTOR);
}

void
ClusterState::removeExtraElements(const NodeType & type)
{
    // Simplify the system state by removing the last indexes if the nodes
    // are down.
    for (int32_t index = _nodeCount[type]; index >= 0; --index) {
        Node node(type, index - 1);
        const auto it = _nodeStates.find(node);
        if (it == _nodeStates.end()) break;
        if (it->second.getState() != State::DOWN) break;
        if (it->second.getDescription() != "") break;
        _nodeStates.erase(it);
        --_nodeCount[type];
    }
}

void
ClusterState::getTextualDifference(std::ostringstream& builder, const NodeType& type, const ClusterState& other) const {
    int maxCount = getNodeCount(type);
    if (other.getNodeCount(type) > maxCount) {
        maxCount = other.getNodeCount(type);
    }

    bool first = true;
    for (int i = 0; i < maxCount; i++) {
        Node n(type, i);
        std::string diff = getNodeState(n).getTextualDifference(other.getNodeState(n));
        if (diff != "no change") {
            if (first) {
                if (builder.str().length() > 0) {
                    builder << " ";
                }
                builder << type << " [";
                first = false;
            } else {
                builder << ", ";
            }
            builder << i << ": " << diff;
        }
    }

    if (!first) {
        builder << "]";
    }
}

std::string
ClusterState::getTextualDifference(const ClusterState& other) const
{
    std::ostringstream builder;

    getTextualDifference(builder, NodeType::STORAGE, other);
    getTextualDifference(builder, NodeType::DISTRIBUTOR, other);

    return builder.str();
}

void
ClusterState::printStateGroupwise(std::ostream& out, const Distribution& dist,
                                  bool verbose, const std::string& indent) const
{
    out << "ClusterState(Version: " << _version << ", Cluster state: " << _clusterState->toString(true)
        << ", Distribution bits: " << _distributionBits << ") {";
    printStateGroupwise(out, dist.getNodeGraph(), verbose, indent + "  ", true);
    out << "\n" << indent << "}";
}

namespace {

template<typename T>
std::string getNumberSpec(const std::vector<T>& numbers) {
    std::ostringstream ost;
    bool first = true;
    uint32_t firstInRange = numbers.size() == 0 ? 0 : numbers[0];;
    uint32_t lastInRange = firstInRange;
    for (uint32_t i=1; i<=numbers.size(); ++i) {
        if (i < numbers.size() && numbers[i] == lastInRange + 1) {
            ++lastInRange;
        } else {
            if (first) {
                first = false;
            } else {
                ost << ",";
            }
            if (firstInRange == lastInRange) {
                ost << firstInRange;
            } else {
                ost << firstInRange << "-" << lastInRange;
            }
            if (i < numbers.size()) {
                firstInRange = lastInRange = numbers[i];
            }
        }
    }
    return ost.str();
}

}

size_t
ClusterState::printStateGroupwise(std::ostream& out, const Group& group, bool verbose,
                                  const std::string& indent, const NodeType& nodeType) const
{
    NodeState defState(nodeType, State::UP);
    size_t printed = 0;
    for (uint16_t nodeId : group.getNodes()) {
        Node node(nodeType, nodeId);
        const NodeState& state(getNodeState(node));
        if (state != defState) {
            out << "\n" << indent << "  " << node << ": ";
            state.print(out, verbose, indent + "    ");
            printed++;
        }
    }
    return printed;
}

void
ClusterState::printStateGroupwise(std::ostream& out, const Group& group, bool verbose,
                                  const std::string& indent, bool rootGroup) const
{
    if (rootGroup) {
        out << "\n" << indent << "Top group";
    } else {
        out << "\n" << indent << "Group " << group.getIndex() << ": " << group.getName();
        if (group.getCapacity() != 1.0) {
            out << ", capacity " << group.getCapacity();
        }
    }
    out << ".";
    if (group.isLeafGroup()) {
        out << " " << group.getNodes().size() << " node" << (group.getNodes().size() != 1 ? "s" : "")
            << " [" << getNumberSpec(group.getNodes()) << "] {";
        size_t printed = printStateGroupwise(out, group, verbose, indent, NodeType::DISTRIBUTOR) +
                         printStateGroupwise(out, group, verbose, indent, NodeType::STORAGE);
        if (printed == 0) {
            out << "\n" << indent << "  All nodes in group up and available.";
        }
    } else {
        const auto & children(group.getSubGroups());
        out << " " << children.size() << " branch" << (children.size() != 1 ? "es" : "")
            << " with distribution " << group.getDistributionSpec() << " {";
        for (const auto & child : children) {
            printStateGroupwise(out, *child.second, verbose,indent + "  ", false);
        }
    }
    out << "\n" << indent << "}";
}

}
