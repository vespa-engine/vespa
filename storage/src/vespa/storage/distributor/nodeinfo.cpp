// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nodeinfo.h"
#include <vespa/storageframework/generic/clock/clock.h>

namespace storage::distributor {

NodeInfo::NodeInfo(const framework::Clock& clock)
    : _clock(clock) {}

uint32_t NodeInfo::getPendingCount(uint16_t idx) const {
    return getNode(idx)._pending;
}

bool NodeInfo::isBusy(uint16_t idx) const {
    const SingleNodeInfo& info = getNode(idx);
    if (info._busyUntilTime.time_since_epoch().count() != 0) {
        if (_clock.getMonotonicTime() > info._busyUntilTime) {
            info._busyUntilTime = vespalib::steady_time();
        } else {
            return true;
        }
    }

    return false;
}

void NodeInfo::setBusy(uint16_t idx, vespalib::duration for_duration) {
    getNode(idx)._busyUntilTime = _clock.getMonotonicTime() + for_duration;
}

void NodeInfo::incPending(uint16_t idx) {
    getNode(idx)._pending++;
}

void NodeInfo::decPending(uint16_t idx) {
    SingleNodeInfo& info = getNode(idx);

    if (info._pending > 0) {
        getNode(idx)._pending--;
    }
}

void NodeInfo::clearPending(uint16_t idx) {
    SingleNodeInfo& info = getNode(idx);
    info._pending = 0;
}

NodeInfo::SingleNodeInfo& NodeInfo::getNode(uint16_t idx) {
    const auto index_lbound = static_cast<size_t>(idx) + 1;
    while (_nodes.size() < index_lbound) {
        _nodes.emplace_back();
    }

    return _nodes[idx];
}

const NodeInfo::SingleNodeInfo& NodeInfo::getNode(uint16_t idx) const {
    const auto index_lbound = static_cast<size_t>(idx) + 1;
    while (_nodes.size() < index_lbound) {
        _nodes.emplace_back();
    }

    return _nodes[idx];
}

}
