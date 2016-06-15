// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <map>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <vespa/vespalib/util/sync.h>
#include <vbench/core/timer.h>
#include "http_connection.h"

namespace vbench {

/**
 * A pool of http connections used to support persistent
 * connections. The pool is shared between threads to reduce the
 * number of needed connections when using many servers.
 **/
class HttpConnectionPool
{
private:
    typedef vespalib::ArrayQueue<HttpConnection::UP> Queue;
    typedef std::map<ServerSpec, size_t> Map;

    vespalib::Lock     _lock;
    Map                _map;
    std::vector<Queue> _store;
    Timer             &_timer;

public:
    HttpConnectionPool(Timer &timer);
    HttpConnection::UP getConnection(const ServerSpec &server);
    void putConnection(HttpConnection::UP conn);
};

} // namespace vbench

