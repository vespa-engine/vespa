// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

class FastOS_ThreadPool;
class FNET_Transport;

namespace proton {

/**
 * Helper class contain a FNET_Transport object for use in tests.
 **/
class TransportMgr {
private:
    std::unique_ptr<FastOS_ThreadPool> _threadPool;
    std::unique_ptr<FNET_Transport>    _transport;

public:
    TransportMgr();
    ~TransportMgr();
    FNET_Transport & transport() { return *_transport; }
    FastOS_ThreadPool & threadPool() { return *_threadPool; }
};

}
