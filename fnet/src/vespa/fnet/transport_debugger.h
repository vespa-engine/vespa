// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transport.h"

#include <vespa/vespalib/util/rendezvous.h>
#include <vespa/vespalib/util/time.h>

#include <memory>

namespace fnet {

/**
 * This class is used to control transport threads during unit
 * testing.
 *
 * The TimeTools created by this class should be used when setting up
 * all transports used in the test. The supplied TimeTools will make
 * sure no thread ever blocks waiting for io-events and also make sure
 * all threads observe the same externally controlled current
 * time. After the transport layers are started, the attach function
 * is used to start controlling event loop execution. While attached,
 * calling the step function will run each transport thread event loop
 * exactly once (in parallel), wait for pending dns resolving, wait
 * for pending tls handshake work and advance the current time (the
 * default 5ms will make sure 'time passes' and 'stuff happens' at a
 * reasonable relative rate). It is important to call detach to
 * release the transports before trying to shut them down.
 *
 * Note that both server and client should be controlled by the same
 * debugger when testing rpc. Using external services will result in
 * (synthetic) time passing too fast compared to stuff actually
 * happening, since you do not control the other end-point in the same
 * way.
 *
 * Take a look at the unit test for this class for an example of how
 * to use it.
 **/
class TransportDebugger {
private:
    struct Meet : vespalib::Rendezvous<bool, bool> {
        Meet(size_t N) : vespalib::Rendezvous<bool, bool>(N) {}
        void mingle() override;
    };
    vespalib::steady_time _time;
    std::shared_ptr<Meet> _meet;

public:
    TransportDebugger();
    ~TransportDebugger();
    vespalib::steady_time time() const { return _time; }
    TimeTools::SP         time_tools() {
        return TimeTools::make_debug(vespalib::duration::zero(), [this]() noexcept { return time(); });
    }
    void                          attach(std::initializer_list<std::reference_wrapper<FNET_Transport>> list);
    void                          step(vespalib::duration time_passed = 5ms);
    template <typename Pred> bool step_until(Pred pred, vespalib::duration time_limit = 120s) {
        auto start = time();
        while (!pred() && ((time() - start) < time_limit)) {
            step();
        }
        return pred();
    }
    void detach();
};

} // namespace fnet
