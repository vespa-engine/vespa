// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_debugger.h"

#include <vespa/vespalib/util/require.h>

#include <vespa/vespalib/util/rendezvous.hpp>

#include <cassert>

namespace fnet {

void TransportDebugger::Meet::mingle() {
    bool call_again = true;
    for (size_t i = 0; i < size(); ++i) {
        if (!in(i)) {
            call_again = false;
        }
    }
    for (size_t i = 0; i < size(); ++i) {
        out(i) = call_again;
    }
}

TransportDebugger::TransportDebugger() : _time(), _meet() {}

TransportDebugger::~TransportDebugger() { assert(!_meet && "error: still attached"); }

void TransportDebugger::attach(std::initializer_list<std::reference_wrapper<FNET_Transport>> list) {
    size_t N = list.size() + 1;
    REQUIRE(!_meet);
    REQUIRE(N > 1);
    _meet = std::make_shared<Meet>(N);
    for (auto& item : list) {
        item.get().attach_capture_hook([meet = _meet]() {
            REQUIRE(meet->rendezvous(true));
            // capture point: between meetings
            return meet->rendezvous(true);
        });
    }
    REQUIRE(_meet->rendezvous(true)); // capture transport threads
}

void TransportDebugger::step(vespalib::duration time_passed) {
    REQUIRE(_meet);
    _time += time_passed;             // pretend time passes between each event loop iteration
    REQUIRE(_meet->rendezvous(true)); // release transport threads
    REQUIRE(_meet->rendezvous(true)); // capture transport threads
}

void TransportDebugger::detach() {
    REQUIRE(_meet);
    REQUIRE(!_meet->rendezvous(false)); // release transport threads (final time)
    _meet.reset();
}

} // namespace fnet
