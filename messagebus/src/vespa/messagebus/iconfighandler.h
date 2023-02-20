// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace mbus {

class RoutingSpec;

/**
 * This interface contains the method(s) used by the ConfigAgent to
 * programmatically configure messagebus. It acts as insulation between
 * the ConfigAgent and MessageBus to simplify testing of the config
 * agent.
 **/
class IConfigHandler
{
public:
    virtual ~IConfigHandler() = default;

    /**
     * This method will be invoked to initialize or change the routing
     * setup. The return value indicates whether the new setup was
     * accepted or not. If false is returned the new routing was
     * rejected and no change in the current setup have been done.
     *
     * @return true if new setup was accepted
     * @param spec spec of new routing setup
     **/
    virtual bool setupRouting(RoutingSpec spec) = 0;
};

} // namespace mbus

