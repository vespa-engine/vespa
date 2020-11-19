// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status.statuspage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows status pages with debug information through a very simple HTTP interface.
 */
public class StatusPageServer {

    public static Logger log = Logger.getLogger(StatusPageServer.class.getName());

    /**
     * Very simple HTTP request class. This should be replaced the second
     * the fleetcontroller e.g. moves into the container.
     */
    public static class HttpRequest {
        private final String request;
        private String pathPrefix = "";
        private final Map<String, String> params = new HashMap<String, String>();
        private String path;

        static Pattern pathPattern;
        static {
            // NOTE: allow [=.] in path to be backwards-compatible with legacy node
            // status pages.
            // If you stare at it for long enough, this sorta looks like one of those
            // magic eye pictures.
            pathPattern = Pattern.compile("^(/([\\w=\\./]+)?)(?:\\?((?:&?\\w+(?:=[\\w\\.]*)?)*))?$");
        }

        public HttpRequest(String request) {
            this.request = request;
            Matcher m = pathPattern.matcher(request);
            if (!m.matches()) {
                throw new IllegalArgumentException("Illegal HTTP request path: " + request);
            }
            path = m.group(1);
            if (m.group(3) != null) {
                String[] rawParams = m.group(3).split("&");
                for (String param : rawParams) {
                    // Parameter values are optional.
                    String[] queryParts = param.split("=");
                    params.put(queryParts[0], queryParts.length > 1 ? queryParts[1] : null);
                }
            }
        }

        public String getPathPrefix() { return pathPrefix; }

        public String toString() {
            return "HttpRequest(" + request + ")";
        }

        public String getRequest() {
            return request;
        }

        public String getPath() {
            return path;
        }

        public boolean hasQueryParameters() {
            return !params.isEmpty();
        }

        public String getQueryParameter(String name) {
            return params.get(name);
        }

        public boolean hasQueryParameter(String name) {
            return params.containsKey(name);
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }
    }

    public interface RequestHandler {
        StatusPageResponse handle(HttpRequest request);
    }

    public interface RequestRouter {
        /**
         * Resolve a request's handler based on its path.
         * @param request HTTP request to resolve for.
         * @return the request handler, or null if none matched.
         */
        RequestHandler resolveHandler(HttpRequest request);
    }

    /**
     * Request router inspired by the Django framework's regular expression
     * based approach. Patterns are matched in the same order as they were
     * added to the router and the first matching one is used as the handler.
     */
    public static class PatternRequestRouter implements RequestRouter {
        private static class PatternRouting {
            public Pattern pattern;
            public RequestHandler handler;

            private PatternRouting(Pattern pattern, RequestHandler handler) {
                this.pattern = pattern;
                this.handler = handler;
            }
        }

        private List<PatternRouting> patterns = new ArrayList<>();

        public void addHandler(Pattern pattern, RequestHandler handler) {
            patterns.add(new PatternRouting(pattern, handler));
        }

        public void addHandler(String pattern, RequestHandler handler) {
            addHandler(Pattern.compile(pattern), handler);
        }

        @Override
        public RequestHandler resolveHandler(HttpRequest request) {
            for (PatternRouting routing : patterns) {
                Matcher m = routing.pattern.matcher(request.getPath());
                if (m.matches()) {
                    return routing.handler;
                }
            }
            return null;
        }
    }

}
