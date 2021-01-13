// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * A cache for an ideal nodes implementation. Making it cheap for localized
 * access, regardless of real implementation.
 */
#pragma once

#include "idealnodecalculator.h"

namespace storage::lib {

class IdealNodeCalculatorImpl : public IdealNodeCalculatorConfigurable {
    std::vector<const char*> _upStates;
    const Distribution* _distribution;
    const ClusterState* _clusterState;

public:
    IdealNodeCalculatorImpl();
    ~IdealNodeCalculatorImpl();

    void setDistribution(const Distribution& d) override;
    void setClusterState(const ClusterState& cs) override;

    IdealNodeList getIdealNodes(const NodeType& nodeType,
                                const document::BucketId& bucket,
                                UpStates upStates) const override;
private:
    void initUpStateMapping();
};

}
