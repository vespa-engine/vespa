// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpcserviceaddress.h"
#include "rpctarget.h"
#include <vespa/messagebus/itimer.h>
#include <vespa/vespalib/util/sync.h>
#include <map>

class FRT_Supervisor;

namespace mbus {

/**
 * Class used to reuse targets for the same address when sending messages over
 * the rpc network.
 */
class RPCTargetPool {
private:
    /**
     * Implements a helper class holds the necessary reference and token counter
     * for a JRT target to keep connections open as long as they get used from
     * time to time.
     */
    struct Entry {
        RPCTarget::SP _target;
        uint64_t      _lastUse;

        Entry(RPCTarget::SP target, uint64_t lastUse);
    };
    using TargetMap = std::map<string, Entry>;
    using LockGuard = std::lock_guard<std::mutex>;

    std::mutex     _lock;
    TargetMap      _targets;
    ITimer::UP     _timer;
    uint64_t       _expireMillis;

public:
    RPCTargetPool(const RPCTargetPool &) = delete;
    RPCTargetPool & operator = (const RPCTargetPool &) = delete;
    /**
     * Constructs a new instance of this class, and registers the {@link
     * SystemTimer} for detecting and closing connections that have expired
     * according to the given parameter.
     *
     * @param expireSecs The number of seconds until an idle connection is
     *                   closed.
     */
    RPCTargetPool(double expireSecs);

    /**
     * Constructs a new instance of this class, using the given {@link Timer}
     * for detecting and closing connections that have expired according to the
     * second paramter.
     *
     * @param timer      The timer to use for connection expiration.
     * @param expireSecs The number of seconds until an idle connection is
     *                   closed.
     */
    RPCTargetPool(ITimer::UP timer, double expireSecs);

    /**
     * Destructor. Frees any allocated resources.
     */
    ~RPCTargetPool();

    /**
     * This method will return a target for the given address. If a target does
     * not currently exist for the given address, it will be created and added
     * to the internal map. Each target is also reference counted so that the
     * tokens of targets that are currently active is never decremented.
     *
     * @param orb     The supervisor to use to connect to the target.
     * @param address The address to resolve to a target.
     * @return A target for the given address.
     */
    RPCTarget::SP getTarget(FRT_Supervisor &orb, const RPCServiceAddress &address);

    /**
     * Closes all unused target connections. Unless the force argument is true,
     * this method will allow a grace period for all connections after last use
     * before it starts closing them. This allows the most recently used
     * connections to stay open.
     *
     * @param force Whether or not to force flush.
     */
    void flushTargets(bool force);

    /**
     * Returns the number of targets currently contained in this.
     *
     * @return The size of the internal map.
     */
    size_t size();
};

} // namespace mbus

