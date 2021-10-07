// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::distributor::NodeInfo
 * \ingroup distributor
 *
 * \brief Keeps track of node state for all storage nodes.
 */
#pragma once

#include <vector>
#include <vespa/storageframework/generic/clock/time.h>

namespace storage::distributor {

class NodeInfo {
public:
    explicit NodeInfo(const framework::Clock& clock);

    uint32_t getPendingCount(uint16_t idx) const;

    bool isBusy(uint16_t idx) const;

    void setBusy(uint16_t idx, framework::MonotonicDuration for_duration);

    void incPending(uint16_t idx);

    void decPending(uint16_t idx);

    void clearPending(uint16_t idx);

private:
    struct SingleNodeInfo {
        SingleNodeInfo() : _pending(0), _busyUntilTime() {}

        uint32_t _pending;
        mutable framework::MonotonicTimePoint _busyUntilTime;
    };

    mutable std::vector<SingleNodeInfo> _nodes;
    const framework::Clock& _clock;

    const SingleNodeInfo& getNode(uint16_t idx) const;
    SingleNodeInfo& getNode(uint16_t idx);
};

}
