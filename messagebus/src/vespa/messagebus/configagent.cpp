// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/routing/routingspec.h>
#include "configagent.h"
#include "iconfighandler.h"

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
    typedef MessagebusConfig CFG;
    for (uint32_t t = 0; t < cfg.routingtable.size(); ++t) {
        const CFG::Routingtable &table = cfg.routingtable[t];
        RoutingTableSpec tableSpec(table.protocol);
        for (uint32_t h = 0; h < table.hop.size(); ++h) {
            const CFG::Routingtable::Hop &hop = table.hop[h];
            HopSpec hopSpec(hop.name, hop.selector);
            for (uint32_t i = 0; i < hop.recipient.size(); ++i) {
                hopSpec.addRecipient(hop.recipient[i]);
            }
            hopSpec.setIgnoreResult(hop.ignoreresult);
            tableSpec.addHop(hopSpec);
        }
        for (uint32_t r = 0; r < table.route.size(); ++r) {
            const CFG::Routingtable::Route &route = table.route[r];
            RouteSpec routeSpec(route.name);
            for (uint32_t i = 0; i < route.hop.size(); ++i) {
                routeSpec.addHop(route.hop[i]);
            }
            tableSpec.addRoute(routeSpec);
        }
        spec.addTable(tableSpec);
    }
    _handler.setupRouting(spec);
}

} // namespace mbus
