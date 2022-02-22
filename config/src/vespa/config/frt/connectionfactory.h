// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

class FNET_Scheduler;

namespace config {

class Connection;

class ConnectionFactory
{
public:
    virtual Connection * getCurrent() = 0;
    virtual void syncTransport() = 0;
    virtual FNET_Scheduler * getScheduler() = 0;
    virtual ~ConnectionFactory() = default;
};

}

