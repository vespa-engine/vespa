// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.routing;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentrouteselectorpolicyConfig;
import com.yahoo.messagebus.routing.ApplicationSpec;
import com.yahoo.messagebus.routing.HopSpec;
import com.yahoo.messagebus.routing.RouteSpec;
import com.yahoo.messagebus.routing.RoutingSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This is the routing plugin of the Vespa model. This class is responsible for parsing all routing information given
 * explicitly by the user in the optional &lt;routing&gt; element. If there is no such element, only default routes and
 * hops will be available.
 *
 * @author Simon Thoresen Hult
 */
public class Routing extends ConfigModel {

    private final List<String> errors = new ArrayList<>();
    private ApplicationSpec explicitApplication = null;
    private RoutingSpec explicitRouting = null;
    private final List<Protocol> protocols = new ArrayList<>();
    private RoutingSpec derivedRouting;

    public Routing(ConfigModelContext modelContext) {
        super(modelContext);
    }

    /**
     * Sets the application specification to include when verifying the complete routing config. This needs to be
     * invoked before {@link #deriveCommonSettings(com.yahoo.config.model.ConfigModelRepo)} to be included.
     *
     * @param app the application specification to include
     */
    public void setExplicitApplicationSpec(ApplicationSpec app) {
        explicitApplication = app;
    }

    /**
     * Sets the routing specification to include in the derived routing config. This needs to be invoked before
     * {@link #deriveCommonSettings(com.yahoo.config.model.ConfigModelRepo)} to be included.
     *
     * @param routing the routing specification to include
     */
    public void setExplicitRoutingSpec(RoutingSpec routing) {
        explicitRouting = routing;
    }

    public final List<Protocol> getProtocols() { return protocols; }

    /**
     * Derives all routing settings that can be found by inspecting the given plugin container.
     *
     * @param plugins all initialized plugins of the vespa model
     */
    public void deriveCommonSettings(ConfigModelRepo plugins) {
        // Combine explicit routing with protocol derived routing.
        ApplicationSpec app = explicitApplication != null ? new ApplicationSpec(explicitApplication) : new ApplicationSpec();
        RoutingSpec routing = explicitRouting != null ? new RoutingSpec(explicitRouting) : new RoutingSpec();
        protocols.clear();
        protocols.add(new DocumentProtocol(plugins));
        for (Protocol protocol : protocols) {
            app.add(protocol.getApplicationSpec());
            addRoutingTable(routing, protocol.getRoutingTableSpec());
        }

        // Add default routes where appropriate, and sort content.
        for (int i = 0, len = routing.getNumTables(); i < len; ++i) {
            RoutingTableSpec table = routing.getTable(i);
            if ( ! table.hasRoute("default") && table.getNumRoutes() == 1) {
                table.addRoute(new RouteSpec("default").addHop("route:" + table.getRoute(0).getName()));
            }
            table.sort();
        }

        // Verify and export all produced configs.
        errors.clear();
        if (routing.verify(app, errors)) {
            this.derivedRouting=routing;
        }
    }

    public void getConfig(DocumentProtocolPoliciesConfig.Builder builder) {
        for (Protocol protocol : protocols) {
            if (protocol instanceof DocumentProtocol) {
                ((DocumentProtocol) protocol).getConfig(builder);
            }
        }
    }

    public void getConfig(DocumentrouteselectorpolicyConfig.Builder builder) {
        for (Protocol protocol : protocols) {
            if (protocol instanceof DocumentProtocol) {
                ((DocumentProtocol)protocol).getConfig(builder);
            }
        }
    }

    public void getConfig(MessagebusConfig.Builder builder) {
        if (derivedRouting == null) {
            // The error list should be populated then
            return;
        }
        if (derivedRouting.hasTables()) {
            for (int tableIdx = 0, numTables = derivedRouting.getNumTables(); tableIdx < numTables; ++tableIdx) {
                RoutingTableSpec table = derivedRouting.getTable(tableIdx);
                MessagebusConfig.Routingtable.Builder tableBuilder = new MessagebusConfig.Routingtable.Builder();
                tableBuilder.protocol(table.getProtocol());
                if (table.hasHops()) {
                    for (int hopIdx = 0, numHops = table.getNumHops(); hopIdx < numHops; ++hopIdx) {
                        MessagebusConfig.Routingtable.Hop.Builder hopBuilder = new MessagebusConfig.Routingtable.Hop.Builder();
                        HopSpec hop = table.getHop(hopIdx);
                        hopBuilder.name(hop.getName());
                        hopBuilder.selector(hop.getSelector());
                        if (hop.getIgnoreResult()) {
                            hopBuilder.ignoreresult(true);
                        }
                        if (hop.hasRecipients()) {
                            for (int recipientIdx = 0, numRecipients = hop.getNumRecipients();
                                 recipientIdx < numRecipients; ++recipientIdx)
                            {
                                hopBuilder.recipient(hop.getRecipient(recipientIdx));
                            }
                        }
                        tableBuilder.hop(hopBuilder);
                    }
                }
                if (table.hasRoutes()) {
                    for (int routeIdx = 0, numRoutes = table.getNumRoutes(); routeIdx < numRoutes; ++routeIdx) {
                        MessagebusConfig.Routingtable.Route.Builder routeBuilder = new MessagebusConfig.Routingtable.Route.Builder();
                        RouteSpec route = table.getRoute(routeIdx);
                        routeBuilder.name(route.getName());
                        if (route.hasHops()) {
                            for (int hopIdx = 0, numHops = route.getNumHops(); hopIdx < numHops; ++hopIdx) {
                                routeBuilder.hop(route.getHop(hopIdx));
                            }
                        }
                        tableBuilder.route(routeBuilder);
                    }
                }
                builder.routingtable(tableBuilder);
            }
        }
    }

    /**
     * Adds the given routing table to the given routing spec. This method will not copy hops or routes that are already
     * defined in the target table.
     *
     * @param routing the routing spec to add to
     * @param from    the table to copy content from
     */
    private static void addRoutingTable(RoutingSpec routing, RoutingTableSpec from) {
        RoutingTableSpec to = getRoutingTable(routing, from.getProtocol());
        if (to != null) {
            Set<String> names = new HashSet<>();
            for (int i = 0, len = to.getNumHops(); i < len; ++i) {
                names.add(to.getHop(i).getName());
            }
            for (int i = 0, len = from.getNumHops(); i < len; ++i) {
                HopSpec hop = from.getHop(i);
                if (!names.contains(hop.getName())) {
                    to.addHop(hop);
                }
            }

            names.clear();
            for (int i = 0, len = to.getNumRoutes(); i < len; ++i) {
                names.add(to.getRoute(i).getName());
            }
            for (int i = 0, len = from.getNumRoutes(); i < len; ++i) {
                RouteSpec route = from.getRoute(i);
                if (!names.contains(route.getName())) {
                    to.addRoute(route);
                }
            }
        } else {
            routing.addTable(from);
        }
    }

    /**
     * Returns the routing table from the given routing spec that belongs to the named protocol.
     *
     * @param routing  the routing whose tables to search through
     * @param protocol the name of the protocol whose table to return
     * @return the routing table found, or null
     */
    private static RoutingTableSpec getRoutingTable(RoutingSpec routing, String protocol) {
        for (int i = 0, len = routing.getNumTables(); i < len; ++i) {
            RoutingTableSpec table = routing.getTable(i);
            if (protocol.equals(table.getProtocol())) {
                return table;
            }
        }
        return null;
    }

    /** Returns a list of errors found when preparing the routing configuration. */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

}
