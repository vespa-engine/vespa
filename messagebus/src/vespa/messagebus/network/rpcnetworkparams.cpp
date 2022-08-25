// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcnetworkparams.h"
#include <vespa/vespalib/util/size_literals.h>

namespace mbus {

RPCNetworkParams::RPCNetworkParams()
    : RPCNetworkParams(config::ConfigUri("client"))
{ }

RPCNetworkParams::RPCNetworkParams(config::ConfigUri configUri) :
    _identity(Identity("")),
    _slobrokConfig(std::move(configUri)),
    _listenPort(0),
    _maxInputBufferSize(256_Ki),
    _maxOutputBufferSize(256_Ki),
    _numNetworkThreads(1),
    _numRpcTargets(1),
    _events_before_wakeup(1),
    _tcpNoDelay(true),
    _connectionExpireSecs(600),
    _compressionConfig(CompressionConfig::LZ4, 6, 90, 1024),
    _required_capabilities(CapabilitySet::make_empty()) // No special peer requirements by default
{ }

RPCNetworkParams::~RPCNetworkParams() = default;

}

