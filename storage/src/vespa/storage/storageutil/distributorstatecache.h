// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>

namespace storage {

class DistributorStateCache
{
public:
    DistributorStateCache(const lib::Distribution& distr, const lib::ClusterState& state) noexcept
        : _distribution(distr),
          _state(state),
          _distrBitMask(0xffffffffffffffffull),
          _lastDistrBits(0xffffffffffffffffull),
          _lastResult(0xffff)
    {
        _distrBitMask <<= (64 - state.getDistributionBitCount());
        _distrBitMask >>= (64 - state.getDistributionBitCount());
    }

    uint16_t getOwner(const document::BucketId& bid, const char* upStates = "ui")
    {
        uint64_t distributionBits = bid.getRawId() & _distrBitMask;

        uint16_t i;
        if (distributionBits == _lastDistrBits) {
            i = _lastResult;
        } else {
            i = _distribution.getIdealDistributorNode(_state, bid, upStates);
        }
        _lastDistrBits = distributionBits;
        _lastResult = i;
        return i;
    }

    const lib::Distribution& getDistribution() const noexcept {
        return _distribution;
    }

    const lib::ClusterState& getClusterState() const noexcept {
        return _state;
    }

private:
    const lib::Distribution& _distribution;
    const lib::ClusterState& _state;
    uint64_t _distrBitMask;
    uint64_t _lastDistrBits;
    uint16_t _lastResult;
};

}

