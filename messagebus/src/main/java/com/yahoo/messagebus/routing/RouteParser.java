// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * This replaces the incredibly slow javacc RouteParser.jj. It is a has its c++ sibling and
 * the implementation is a a copy of the C++ version.
 * @author baldersheim
 * @since 5.2
 */

public class RouteParser {
    private final String routeText;
    RouteParser(String route) {
        this.routeText = route;
    }

    Route route() {
        Route route = new Route();
        for (int from = 0, at = 0, depth = 0; at <= routeText.length(); ++at) {
            if (at == routeText.length() || ((depth == 0) && Character.isWhitespace(routeText.charAt(at)))) {
                if (from < at) {
                    Hop hop = createHop(routeText.substring(from, at));
                    if (hop.hasDirectives() && hop.getDirective(0) instanceof ErrorDirective) {
                        return new Route().addHop(new Hop().addDirective(hop.getDirective(0)));
                    }
                    route.addHop(hop);
                }
                from = at + 1;
            } else if ((routeText.charAt(at) == '(') || (routeText.charAt(at) == '[') ) {
                ++depth;
            } else if ((routeText.charAt(at) == ')') || (routeText.charAt(at) == ']')) {
                --depth;
            }
        }
        return route;
    }
    private static Hop createHop(String s) {
        final int len = s.length();
        if (len == 0) {
            return new Hop().addDirective(createErrorDirective("Failed to parse empty string."));
        } else if (len > 1 && (s.charAt(0) == '?')) {
            return createHop(s.substring(1, len)).setIgnoreResult(true);
        } else if (len > 4 && s.charAt(0) == 't' && s.charAt(1) == 'c' && s.charAt(2) == 'p' && s.charAt(3) == '/') {
            HopDirective directive = createTcpDirective(s.substring(4, len));
            if (directive != null) {
                return new Hop().addDirective(directive);
            }
        } else if (len > 6 && s.charAt(0) == 'r' && s.charAt(1) == 'o' && s.charAt(2) == 'u'
                           && s.charAt(3) == 't' && s.charAt(4) == 'e' && s.charAt(5) == ':') {
            return new Hop().addDirective(createRouteDirective(s.substring(6, len)));
        }
        Hop hop = new Hop();
        for (int from = 0, at = 0, depth = 0; at <= len; ++at) {
            if (at == len) {
                if (depth > 0) {
                    return new Hop().addDirective(createErrorDirective("Unterminated '[' in '" + s + "'"));
                }
                hop.addDirective(createDirective(s.substring(from, at)));
                from = at + 1;
            } else {
                char c = s.charAt(at);
                if (Character.isWhitespace(c) && depth == 0) {
                    return new Hop().addDirective(createErrorDirective("Failed to completely parse '" + s + "'."));
                } else if ((depth == 0 && c == '/')) {
                    hop.addDirective(createDirective(s.substring(from, at)));
                    from = at + 1;
                } else if (c == '[') {
                    ++depth;
                } else if (c == ']') {
                    if (depth == 0) {
                        return new Hop().addDirective(createErrorDirective("Unexpected token ']' in '" + s + "'"));
                    }
                    --depth;
                }
            }
        }
        return hop;
    }
    private static HopDirective createErrorDirective(String s) {
        return new ErrorDirective(s);
    }
    private static HopDirective createTcpDirective(String s) {
        int posP = s.indexOf(':');
        if (posP <= 0) {
            return null;
        }
        int posS = s.indexOf('/', posP+1);
        if (posS <= posP + 1) {
            return null;
        }
        return new TcpDirective(s.substring(0, posP),
                Integer.valueOf(s.substring(posP + 1, posS)),
                s.substring(posS + 1));
    }
    private static HopDirective createRouteDirective(String s) {
        return new RouteDirective(s);
    }
    private static HopDirective createDirective(String s) {
        return (s.length() > 2 && s.charAt(0) == '[')
                ? ((s.charAt(s.length() - 1) == ']')
                    ? createPolicyDirective(s.substring(1, s.length() - 1))
                    : createErrorDirective("Unterminated '[' in '" + s + "'"))
                : createVerbatimDirective(s);

    }
    private static HopDirective createPolicyDirective(String s)
    {
        int pos = s.indexOf(':');
        return (pos == -1)
                ? new PolicyDirective(s, "")
                : new PolicyDirective(s.substring(0, pos), s.substring(pos + 1).trim());
    }

    private static HopDirective createVerbatimDirective(String s) {
        return new VerbatimDirective(s);
    }

}
