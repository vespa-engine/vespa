// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.application.Xml;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.messagebus.routing.ApplicationSpec;
import com.yahoo.messagebus.routing.HopSpec;
import com.yahoo.messagebus.routing.RouteSpec;
import com.yahoo.messagebus.routing.RoutingSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.routing.Routing;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.List;

/**
 * Builds the Routing plugin
 *
 * @author vegardh
 */
public class DomRoutingBuilder extends ConfigModelBuilder<Routing> {

    public DomRoutingBuilder() {
        super(Routing.class);
    }

    @Override
    public List<ConfigModelId> handlesElements() {
        return List.of(ConfigModelId.fromName("routing"));
    }

    // Overrides ConfigModelBuilder.
    @Override
    public void doBuild(Routing plugin, Element spec, ConfigModelContext modelContext) {
        ApplicationSpec app = null;
        RoutingSpec routing = null;
        if (spec != null) {
            app = new ApplicationSpec();
            for (Element node : Xml.mergeElems(spec, "services", modelContext.getApplicationPackage(), ApplicationPackage.ROUTINGTABLES_DIR)) {
                addServices(app, node);
            }
            routing = new RoutingSpec();
            for (Element node : Xml.mergeElems(spec, "routingtable", modelContext.getApplicationPackage(), ApplicationPackage.ROUTINGTABLES_DIR)) {
                addRoutingTable(routing, node);
            }
        }
        plugin.setExplicitApplicationSpec(app);
        plugin.setExplicitRoutingSpec(routing);
    }

    /**
     * This function updates the given application with the data contained in the given xml element.
     *
     * @param app The application to update.
     * @param element The element to base the services on.
     */
    private static void addServices(ApplicationSpec app, Element element) {
        String protocol = element.getAttribute("protocol");
        for (Element node : XML.getChildren(element, "service")) {
            app.addService(protocol, node.getAttribute("name"));
        }
    }

    /**
     * This function updates the given routing spec with the data contained in the given xml element.
     *
     * @param routing The routing spec to update.
     * @param element The element to base the route config on.
     */
    private static void addRoutingTable(RoutingSpec routing, Element element) {
        boolean verify = shouldVerify(element);
        RoutingTableSpec table = new RoutingTableSpec(element.getAttribute("protocol"), verify);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if ("hop".equals(node.getNodeName())) {
                table.addHop(createHopSpec((Element)node));
            } else if ("route".equals(node.getNodeName())) {
                table.addRoute(createRouteSpec((Element)node));
            }
        }

        routing.addTable(table);
    }

    /**
     * This function creates a route from the content of the given xml element.
     *
     * @param element The element to base the route config on.
     * @return The corresponding route spec.
     */
    private static RouteSpec createRouteSpec(Element element) {
        boolean verify = shouldVerify(element);
        RouteSpec route = new RouteSpec(element.getAttribute("name"), verify);
        String hops = element.getAttribute("hops");
        int from = 0;
        for (int to = 0, depth = 0, len = hops.length(); to < len; ++to) {
            if (hops.charAt(to) == '[') {
                ++depth;
            } else if (hops.charAt(to) == ']') {
                --depth;
            } else if (hops.charAt(to) == ' ' && depth == 0) {
                if (to > from) {
                    route.addHop(hops.substring(from, to));
                }
                from = to + 1;
            }
        }
        if (from < hops.length()) {
            route.addHop(hops.substring(from));
        }
        return route;
    }

    /**
     * This function creates a hop from the content of the given xml element.
     *
     * @param element The element to base the hop config on.
     * @return The corresponding hop spec.
     */
    private static HopSpec createHopSpec(Element element) {
        boolean verify = shouldVerify(element);
        HopSpec hop = new HopSpec(element.getAttribute("name"), element.getAttribute("selector"), verify);
        if (Boolean.parseBoolean(element.getAttribute("ignore-result"))) {
            hop.setIgnoreResult(true);
        }
        NodeList children = element.getElementsByTagName("recipient");
        for (int i = 0; i < children.getLength(); i++) {
            Element node = (Element)children.item(i);
            hop.addRecipient(node.getAttribute("session"));
        }
        return hop;
    }

    private static boolean shouldVerify(Element element) {
        return !element.hasAttribute("verify") || Boolean.parseBoolean(element.getAttribute("verify"));
    }
}
