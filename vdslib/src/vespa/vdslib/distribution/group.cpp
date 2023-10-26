// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "group.h"

#include <vespa/vdslib/state/random.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>
#include <cassert>
#include <algorithm>

namespace storage::lib {

Group::Group(uint16_t index, vespalib::stringref name) noexcept
    : _name(name),
      _index(index),
      _distributionHash(0),
      _capacity(1.0),
      _subGroups(),
      _nodes()
{
}

Group::Group(uint16_t index, vespalib::stringref name,
             const Distribution& d, uint16_t redundancy)
    : _name(name),
      _index(index),
      _distributionHash(0),
      _distributionSpec(d),
      _preCalculated(redundancy + 1),
      _capacity(1.0),
      _subGroups(),
      _nodes()
{
    for (uint32_t i=0; i<_preCalculated.size(); ++i) {
        _preCalculated[i] = Distribution(d, i);
    }
}

Group::~Group()
{
    for (auto & subGroup : _subGroups) {
        delete subGroup.second;
        subGroup.second = nullptr;
    }
}

bool
Group::operator==(const Group& other) const noexcept
{
    return (_name == other._name &&
            _index == other._index &&
            _distributionSpec == other._distributionSpec &&
            _preCalculated.size() == other._preCalculated.size() &&
            _capacity == other._capacity &&
            _subGroups == other._subGroups &&
            _nodes == other._nodes);
}

void
Group::print(std::ostream& out, bool verbose,
             const std::string& indent) const {
    out << "Group(";
    if (!_name.empty()) {
        out << "name: " << _name << ", ";
    }
    out << "index: " << _index;
    if (_distributionSpec.size() > 0) {
        out << ", distribution: " << _distributionSpec;
    }
    if (_capacity != 1.0) {
        out << ", capacity: " << _capacity;
    }
    if (_distributionSpec.size() == 0) {
        out << ", nodes( ";
        for (auto node : _nodes) {
            out << node << " ";
        }
        out << ")";
    }

    if (_subGroups.size()>0) {
        out << ", subgroups: " << _subGroups.size();
    }

    out << ") {";

    if (_subGroups.size()>0) {
        for (const auto & subGroup : _subGroups) {
            out  << "\n" << indent << "  ";
            subGroup.second->print(out, verbose, indent + "  ");
        }
    }

    out << "\n" << indent << "}";
}

void
Group::addSubGroup(Group::UP group)
{
    if (_distributionSpec.size() == 0) {
        throw vespalib::IllegalStateException(
                "Cannot add sub groups to a group without a valid distribution",
                VESPA_STRLOC);
    }
    if (!group) {
        throw vespalib::IllegalArgumentException(
                "Cannot add null group.", VESPA_STRLOC);
    }
    auto it =_subGroups.find(group->getIndex());
    if (it != _subGroups.end()) {
        throw vespalib::IllegalArgumentException(
                "Another subgroup with same index is already added.",
                VESPA_STRLOC);
    }
    auto index = group->getIndex();
    _subGroups[index] = group.release();
}

void
Group::setCapacity(vespalib::Double capacity)
{
    if (capacity <= 0) {
        vespalib::asciistream ost;
        ost << "Illegal capacity '" << capacity << "'. Capacity "
            "must be a positive floating point number";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    _capacity = capacity;
}

void
Group::setNodes(const std::vector<uint16_t>& nodes)
{
    assert(_distributionSpec.size() == 0);
    _originalNodes = nodes;
    _nodes = nodes;
    // Maintain ordering invariant. Required to ensure node score computations
    // finish in linear time. Failure to maintain invariant may result in
    // quadratic worst case behavior.
    std::sort(_nodes.begin(), _nodes.end());
}

const Group*
Group::getGroupForNode(uint16_t nodeIdx) const
{
    for (auto node : _nodes) {
        if (node == nodeIdx) {
            return this;
        }
    }

    for (const auto & subGroup : _subGroups) {
        const Group* g = subGroup.second->getGroupForNode(nodeIdx);
        if (g != nullptr) {
            return g;
        }
    }

    return nullptr;
}

void
Group::calculateDistributionHashValues(uint32_t parentHash)
{
    _distributionHash = parentHash ^ (1664525L * _index + 1013904223L);
    for (const auto & subGroup : _subGroups) {
        subGroup.second->calculateDistributionHashValues(_distributionHash);
    }
}

void
Group::getConfigHash(vespalib::asciistream& out) const
{
    out << '(' << _index;
    if (_capacity != 1.0) {
        out << 'c' << _capacity;
    }
    if (isLeafGroup()) {
        for (uint16_t node : _originalNodes) {
            out << ';' << node;
        }
    } else {
        out << 'd' << _distributionSpec.toString();
        for (const auto& subGroup : _subGroups) {
            subGroup.second->getConfigHash(out);
        }
    }
    out << ')';
}

vespalib::string
Group::getDistributionConfigHash() const {
    vespalib::asciistream ost;
    getConfigHash(ost);
    return ost.str();
}

}
