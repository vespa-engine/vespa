// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import com.yahoo.vespa.hosted.routing.RoutingTable;
import com.yahoo.vespa.hosted.routing.RoutingTable.Real;
import com.yahoo.vespa.hosted.routing.status.RoutingStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts a {@link RoutingTable} to Nginx's own config format.
 *
 * @author mpolden
 */
class NginxConfig {

    private NginxConfig() {
    }

    public static String from(RoutingTable routingTable, RoutingStatus routingStatus) {
        StringBuilder sb = new StringBuilder();

        // Map SNI header to upstream
        sb.append("map $ssl_preread_server_name $name {\n");
        routingTable.asMap().forEach((endpoint, target) -> {
            sb.append("   ").append(endpoint.dnsName()).append(" ").append(target.id()).append(";\n");
        });

        // Forward requests without SNI header directly to Nginx (e.g. VIP health checks)
        sb.append("   '' default;\n");
        sb.append("}\n\n");

        // Render routing table targets as upstreams
        renderUpstreamsTo(sb, routingTable, routingStatus);

        // Configure the default upstream, which targets Nginx itself
        sb.append("upstream default {\n");
        sb.append("   server localhost:4445;\n");
        sb.append("   ").append(checkDirective(4080)).append("\n");
        sb.append("   ").append(checkHttpSendDirective("localhost")).append("\n");
        sb.append("}\n\n");

        // Listener port
        sb.append("server {\n");
        sb.append("   listen 443 reuseport;\n");
        sb.append("   listen [::]:443 reuseport;\n");
        sb.append("   proxy_pass $name;\n");
        sb.append("   ssl_preread on;\n");
        sb.append("   proxy_protocol on;\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String checkDirective(int port) {
        // nginx_http_upstream_check_module does not support health checks over https
        // a different http port is used instead, which acts as a http->https proxy for /status.html requests
        return String.format("check interval=2000 fall=5 rise=2 timeout=3000 default_down=true type=http port=%d;",
                             port);
    }

    private static String checkHttpSendDirective(String upstreamName) {
        return "check_http_send \"" +
               "GET /status.html HTTP/1.0\\r\\n" +
               "Host: " + upstreamName + "\\r\\n" +
               "\\r\\n\";";
    }

    private static void renderUpstreamsTo(StringBuilder sb, RoutingTable routingTable, RoutingStatus routingStatus) {
        Map<Real, RoutingTable.Target> realTable = new HashMap<>();
        for (var target : routingTable.asMap().values()) {
            if (target.applicationLevel()) continue;
            for (var real : target.reals()) {
                realTable.put(real, target);
            }
        }
        routingTable.asMap().values().stream().sorted().distinct().forEach(target -> {
            sb.append("upstream ").append(target.id()).append(" {").append("\n");

            // Check if any target is active.
            for (var real : target.reals()) {
                boolean explicitRoutingActive = true;
                // Check external status service if this is an application-level target
                if (target.applicationLevel()) {
                    RoutingTable.Target targetOfReal = realTable.get(real);
                    explicitRoutingActive = routingStatus.isActive(targetOfReal.id());
                }
                String serverParameter = serverParameter(target, real, explicitRoutingActive);
                sb.append("  server ").append(real.hostname()).append(":4443").append(serverParameter).append(";\n");
            }
            int healthCheckPort = 4082;
            sb.append("  ").append(checkDirective(healthCheckPort)).append("\n");
            sb.append("  ").append(checkHttpSendDirective(target.id())).append("\n");
            sb.append("  random two;\n");
            sb.append("}\n\n");
        });
    }

    private static String serverParameter(RoutingTable.Target target, Real real, boolean routingActive) {
        // For each real consider:
        // * if not an application-level target -> no parameters
        // * if active & routingActive = false AND the upstream contains at least one active host -> "down"
        // * if weight assigned = 0 -> "backup"
        // * if weight assigned > 0 -> "weight=<weight>"
        if (!target.applicationLevel()) return "";
        if (!(real.active() && routingActive) && target.active()) return " down";
        int weight = real.weight();
        if (weight == 0) {
            return " backup";
        } else {
            return " weight=" + weight;
        }
    }

}
