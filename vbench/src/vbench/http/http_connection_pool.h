// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "http_connection.h"
#include <vbench/core/timer.h>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <map>

namespace vbench {

/**
 * A pool of http connections used to support persistent
 * connections. The pool is shared between threads to reduce the
 * number of needed connections when using many servers.
 **/
class HttpConnectionPool
{
private:
    using Queue = vespalib::ArrayQueue<HttpConnection::UP>;
    using Map = std::map<ServerSpec, size_t>;
    using CryptoEngine = vespalib::CryptoEngine;

    std::mutex         _lock;
    Map                _map;
    std::vector<Queue> _store;
    CryptoEngine::SP   _crypto;
    Timer             &_timer;

public:
    HttpConnectionPool(CryptoEngine::SP crypto, Timer &timer);
    ~HttpConnectionPool();
    CryptoEngine &crypto() { return *_crypto; }
    HttpConnection::UP getConnection(const ServerSpec &server);
    void putConnection(HttpConnection::UP conn);
};

} // namespace vbench

