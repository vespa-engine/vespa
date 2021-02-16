// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcnetworkparams.h"
#include <vespa/vespalib/util/size_literals.h>

namespace mbus {

RPCNetworkParams::RPCNetworkParams()
    : RPCNetworkParams("admin/slobrok.0")
{ }

RPCNetworkParams::RPCNetworkParams(config::ConfigUri configUri) :
    _identity(Identity("")),
    _slobrokConfig(std::move(configUri)),
    _listenPort(0),
    _maxInputBufferSize(256_Ki),
    _maxOutputBufferSize(256_Ki),
    _numThreads(4),
    _tcpNoDelay(true),
    _dispatchOnEncode(true),
    _dispatchOnDecode(false),
    _skip_request_thread(false),
    _skip_reply_thread(false),
    _connectionExpireSecs(600),
    _compressionConfig(CompressionConfig::LZ4, 6, 90, 1024)
{ }

RPCNetworkParams::~RPCNetworkParams() = default;

}

