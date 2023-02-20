// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configagent.h"
#include "iconfighandler.h"
#include <vespa/messagebus/routing/routingspec.h>

using namespace config;
using namespace messagebus;

namespace mbus {

ConfigAgent::ConfigAgent(IConfigHandler & handler)
    : _handler(handler)
{ }

void
ConfigAgent::configure(std::unique_ptr<MessagebusConfig> config)
{
    const MessagebusConfig &cfg(*config);
    RoutingSpec spec;
    for (const auto & table : cfg.routingtable) {
        RoutingTableSpec tableSpec(table.protocol);
        for (const auto & hop : table.hop) {
            HopSpec hopSpec(hop.name, hop.selector);
            for (const auto & i : hop.recipient) {
                hopSpec.addRecipient(i);
            }
            hopSpec.setIgnoreResult(hop.ignoreresult);
            tableSpec.addHop(std::move(hopSpec));
        }
        for (const auto & route : table.route) {
            RouteSpec routeSpec(route.name);
            for (const auto & i : route.hop) {
                routeSpec.addHop(i);
            }
            tableSpec.addRoute(std::move(routeSpec));
        }
        spec.addTable(std::move(tableSpec));
    }
    _handler.setupRouting(spec);
}

} // namespace mbus
