// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_helper.h"
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <vespa/vespalib/util/size_literals.h>

namespace proton {

TransportMgr::TransportMgr()
    : _threadPool(std::make_unique<FastOS_ThreadPool>(64_Ki)),
      _transport(std::make_unique<FNET_Transport>())
{
    _transport->Start(_threadPool.get());
}

TransportMgr::~TransportMgr() {
    _transport->ShutDown(true);
}

}
