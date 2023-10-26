// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace slobrok {

//-----------------------------------------------------------------------------

/**
 * @class IMonitoredServer
 * @brief A server that is monitored by a Monitor object
 *
 * interface that must be implemented by owners of Monitor objects.
 **/
class IMonitoredServer
{
public:
    virtual void notifyDisconnected() = 0; // lost connection to service
    virtual ~IMonitoredServer() {}
};

//-----------------------------------------------------------------------------

} // namespace slobrok

