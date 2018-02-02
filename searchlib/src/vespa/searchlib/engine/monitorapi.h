// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "monitorrequest.h"
#include "monitorreply.h"

namespace search::engine {

/**
 * A monitor client is the object being notified of the completion of
 * an asynchronous monitor operation.
 **/
class MonitorClient
{
public:
    /**
     * Invoked by the monitor server to indicate the completion of an
     * asynchronous monitor operation.
     *
     * @param reply the monitor reply
     **/
    virtual void pingDone(MonitorReply::UP reply) = 0;

    /**
     * Empty, needed for subclassing
     **/
    virtual ~MonitorClient() {}
};

/**
 * A monitor server is an object capable of performing a monitor
 * operation.
 **/
class MonitorServer
{
public:
    /**
     * Initiate a monitor operation that can be completed either
     * synchronously or asynchronously. The return value will indicate
     * whether the server selected to perform the operation
     * synchronously or asynchronously. If the return value contains
     * an object, then the operation completed synchronously and no
     * further action will be taken by the server. If the return value
     * did not contain an object, the operation will continue
     * asynchronously, and the given client will be notified when the
     * operation is completed. The server is not allowed to signal an
     * asynchronous completion of the operation in the context of this
     * method invocation.
     *
     * @return actual return value if sync, 'null' if async
     * @param request object containing request parameters
     * @param client the client to be notified of async completion
     **/
    virtual MonitorReply::UP ping(MonitorRequest::UP request, MonitorClient &client) = 0;

    /**
     * Empty, needed for subclassing
     **/
    virtual ~MonitorServer() {}
};

}

