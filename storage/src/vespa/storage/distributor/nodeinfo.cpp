// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nodeinfo.h"
#include <vespa/storageframework/generic/clock/clock.h>

namespace storage::distributor {

NodeInfo::NodeInfo(const framework::Clock& clock)
        : _clock(clock) {}

uint32_t
NodeInfo::getPendingCount(uint16_t idx) const
{
    return getNode(idx)._pending;
}

bool
NodeInfo::isBusy(uint16_t idx) const
{
    const SingleNodeInfo& info = getNode(idx);
    if (info._busyTime.isSet()) {
        if (_clock.getTimeInSeconds() > info._busyTime) {
            info._busyTime = framework::SecondTime(0);
        } else {
            return true;
        }
    }

    return false;
}

void
NodeInfo::setBusy(uint16_t idx)
{
    getNode(idx)._busyTime = _clock.getTimeInSeconds()
                           + framework::SecondTime(60);
}

void
NodeInfo::incPending(uint16_t idx)
{
    getNode(idx)._pending++;
}

void
NodeInfo::decPending(uint16_t idx)
{
    SingleNodeInfo& info = getNode(idx);

    if (info._pending > 0) {
        getNode(idx)._pending--;
    }
}

void
NodeInfo::clearPending(uint16_t idx)
{
    SingleNodeInfo& info = getNode(idx);
    info._pending = 0;
}

NodeInfo::SingleNodeInfo&
NodeInfo::getNode(uint16_t idx)
{
    while ((int)_nodes.size() < idx + 1) {
        _nodes.push_back(SingleNodeInfo());
    }

    return _nodes[idx];
}

const NodeInfo::SingleNodeInfo&
NodeInfo::getNode(uint16_t idx) const
{
    while ((int)_nodes.size() < idx + 1) {
        _nodes.push_back(SingleNodeInfo());
    }

    return _nodes[idx];
}

}
