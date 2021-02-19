// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "idealnodecalculatorimpl.h"
#include "distribution.h"
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>
#include <cassert>

namespace storage::lib {

IdealNodeList::IdealNodeList() = default;
IdealNodeList::~IdealNodeList() = default;

void
IdealNodeList::print(std::ostream& out, bool , const std::string &) const
{
    out << "[";
    for (uint32_t i=0; i<_idealNodes.size(); ++i) {
        if (i != 0) out << ", ";
        out << _idealNodes[i];
    }
    out << "]";
}

IdealNodeCalculatorImpl::IdealNodeCalculatorImpl()
    : _distribution(0),
      _clusterState(0)
{
    initUpStateMapping();
}

IdealNodeCalculatorImpl::~IdealNodeCalculatorImpl() = default;

void
IdealNodeCalculatorImpl::setDistribution(const Distribution& d) {
    _distribution = &d;
}
void
IdealNodeCalculatorImpl::setClusterState(const ClusterState& cs) {
    _clusterState = &cs;
}

IdealNodeList
IdealNodeCalculatorImpl::getIdealNodes(const NodeType& nodeType,
                                       const document::BucketId& bucket,
                                       UpStates upStates) const
{
    assert(_clusterState != 0);
    assert(_distribution != 0);
    std::vector<uint16_t> nodes;
    _distribution->getIdealNodes(nodeType, *_clusterState, bucket, nodes, _upStates[upStates]);
    IdealNodeList list;
    for (uint32_t i=0; i<nodes.size(); ++i) {
        list.push_back(Node(nodeType, nodes[i]));
    }
    return list;
}

void
IdealNodeCalculatorImpl::initUpStateMapping() {
    _upStates.clear();
    _upStates.resize(UP_STATE_COUNT);
    _upStates[UpInit] = "ui";
    _upStates[UpInitMaintenance] = "uim";
    for (uint32_t i=0; i<_upStates.size(); ++i) {
        if (_upStates[i] == 0) throw vespalib::IllegalStateException(
                "Failed to initialize up state. Code likely not updated "
                "after another upstate was added.", VESPA_STRLOC);
    }
}

}
