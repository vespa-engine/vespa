// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * A cache for an ideal nodes implementation. Making it cheap for localized
 * access, regardless of real implementation.
 */
#pragma once

#include <vespa/vdslib/distribution/idealnodecalculator.h>

namespace storage {
namespace lib {

class IdealNodeCalculatorImpl : public IdealNodeCalculatorConfigurable {
    std::vector<const char*> _upStates;
    const Distribution* _distribution;
    const ClusterState* _clusterState;

public:
    IdealNodeCalculatorImpl()
        : _distribution(0),
          _clusterState(0)
    {
        initUpStateMapping();
    }

    virtual void setDistribution(const Distribution& d) {
        _distribution = &d;
    }
    virtual void setClusterState(const ClusterState& cs) {
        _clusterState = &cs;
    }

    virtual IdealNodeList getIdealNodes(const NodeType& nodeType,
                                        const document::BucketId& bucket,
                                        UpStates upStates) const
    {
        assert(_clusterState != 0);
        assert(_distribution != 0);
        std::vector<uint16_t> nodes;
        _distribution->getIdealNodes(nodeType, *_clusterState, bucket, nodes,
                                     _upStates[upStates]);
        IdealNodeList list;
        for (uint32_t i=0; i<nodes.size(); ++i) {
            list.push_back(Node(nodeType, nodes[i]));
        }
        return list;
    }

private:
    void initUpStateMapping() {
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
};

} // lib
} // storage
