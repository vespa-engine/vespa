// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "loadbalancer.h"

namespace documentapi {

LoadBalancer::LoadBalancer(const string& cluster, const string& session)
    : _mutex(),
      _nodeInfo(),
      _cluster(cluster),
      _session(session),
      _position(0)
{}

LoadBalancer::~LoadBalancer() = default;

uint32_t
LoadBalancer::getIndex(const string& name) const
{
    size_t lastSlash = name.find('/');
    string idx = name.substr(_cluster.length() + 1, lastSlash);
    return atoi(idx.c_str());
}

string
LoadBalancer::getLastSpec(size_t target) const {
    lock_guard guard(_mutex);
    return _nodeInfo[target].lastSpec;
}

double
LoadBalancer::getWeight(size_t target) const {
    lock_guard guard(_mutex);
    return _nodeInfo[target].weight;
}

std::pair<string, int>
LoadBalancer::getRecipient(const slobrok::api::IMirrorAPI::SpecList& choices) {
    lock_guard guard(_mutex);
    return getRecipient(guard, choices);
}

std::pair<string, int>
LoadBalancer::getRecipient(const lock_guard & guard, const slobrok::api::IMirrorAPI::SpecList& choices) {
    std::pair<string, int> retVal("", -1);

    if (choices.size() == 0) {
        return retVal;
    }

    double weightSum = 0.0;

    for (uint32_t i = 0; i < choices.size(); i++) {
        const std::pair<string, string>& curr = choices[i];

        uint32_t index = getIndex(curr.first);

        if (_nodeInfo.size() < (index + 1)) {
            _nodeInfo.resize(index + 1);
        }

        NodeInfo& info = _nodeInfo[index];
        info.valid = true;
        weightSum += info.weight;

        if (weightSum > _position) {
            retVal.first = curr.second;
            retVal.second = index;
            info.lastSpec = retVal.first;
            break;
        }
    }

    if (retVal.second == -1) {
        _position -= weightSum;
        return getRecipient(guard, choices);
    } else {
        _position += 1.0;
    }

    return retVal;
}

void
LoadBalancer::normalizeWeights(const lock_guard &) {
    double lowest = -1.0;

    for (uint32_t i = 0; i < _nodeInfo.size(); i++) {
        if (!_nodeInfo[i].valid) {
            continue;
        }

        if (lowest < 0 || _nodeInfo[i].weight < lowest) {
            lowest = _nodeInfo[i].weight;
        }
    }

    for (uint32_t i = 0; i < _nodeInfo.size(); i++) {
        if (!_nodeInfo[i].valid) {
            continue;
        }

        _nodeInfo[i].weight = _nodeInfo[i].weight / lowest;
    }
}

void
LoadBalancer::received(uint32_t nodeIndex, bool busy) {
    if (busy) {
        lock_guard guard(_mutex);
        NodeInfo& info = _nodeInfo[nodeIndex];

        info.weight = info.weight - 0.01;
        normalizeWeights(guard);
    }
}

}
