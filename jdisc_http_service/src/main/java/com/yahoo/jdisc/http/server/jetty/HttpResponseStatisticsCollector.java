// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer.Metrics;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * HttpResponseStatisticsCollector collects statistics about HTTP response types aggregated by category
 * (1xx, 2xx, etc).
 *
 * @author ollivir
 * @author bratseth
 */
public class HttpResponseStatisticsCollector {

    private final List<String> monitoringHandlerPaths;
    private final List<String> searchHandlerPaths;

    public enum HttpMethod {
        GET, PATCH, POST, PUT, DELETE, OPTIONS, HEAD, OTHER
    }

    public enum HttpScheme {
        HTTP, HTTPS, OTHER
    }

    private static final String[] HTTP_RESPONSE_GROUPS = {
            Metrics.RESPONSES_1XX,
            Metrics.RESPONSES_2XX,
            Metrics.RESPONSES_3XX,
            Metrics.RESPONSES_4XX,
            Metrics.RESPONSES_5XX,
            Metrics.RESPONSES_401,
            Metrics.RESPONSES_403
    };

    private final LongAdder[][][][] statistics;

    public HttpResponseStatisticsCollector(List<String> monitoringHandlerPaths, List<String> searchHandlerPaths) {
        this.monitoringHandlerPaths = monitoringHandlerPaths;
        this.searchHandlerPaths = searchHandlerPaths;
        statistics = new LongAdder[HttpScheme.values().length][HttpMethod.values().length][][];
        for (int scheme = 0; scheme < HttpScheme.values().length; ++scheme) {
            for (int method = 0; method < HttpMethod.values().length; method++) {
                statistics[scheme][method] = new LongAdder[HTTP_RESPONSE_GROUPS.length][];
                for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                    statistics[scheme][method][group] = new LongAdder[Response.RequestType.values().length];
                    for (int requestType = 0; requestType < Response.RequestType.values().length; requestType++) {
                        statistics[scheme][method][group][requestType] = new LongAdder();
                    }
                }
            }
        }
    }

    void observeEndOfRequest(HttpServletRequest request, Response jdiscResponse) {
        int group = groupIndex(jdiscResponse);
        if (group >= 0) {
            HttpScheme scheme = getScheme(request);
            HttpMethod method = getMethod(request);
            Response.RequestType requestType = getRequestType(request, jdiscResponse);

            statistics[scheme.ordinal()][method.ordinal()][group][requestType.ordinal()].increment();
            if (group == 5 || group == 6) { // if 401/403, also increment 4xx
                statistics[scheme.ordinal()][method.ordinal()][3][requestType.ordinal()].increment();
            }
        }
    }

    private int groupIndex(Response response) {
        int index = response.getStatus();
        if (index == 401) {
            return 5;
        }
        if (index == 403) {
            return 6;
        }

        index = index / 100 - 1; // 1xx = 0, 2xx = 1 etc.
        if (index < 0 || index >= statistics[0].length) {
            return -1;
        } else {
            return index;
        }
    }

    private HttpScheme getScheme(HttpServletRequest request) {
        switch (request.getScheme()) {
            case "http":
                return HttpScheme.HTTP;
            case "https":
                return HttpScheme.HTTPS;
            default:
                return HttpScheme.OTHER;
        }
    }

    private HttpMethod getMethod(HttpServletRequest request) {
        switch (request.getMethod()) {
        case "GET":
            return HttpMethod.GET;
        case "PATCH":
            return HttpMethod.PATCH;
        case "POST":
            return HttpMethod.POST;
        case "PUT":
            return HttpMethod.PUT;
        case "DELETE":
            return HttpMethod.DELETE;
        case "OPTIONS":
            return HttpMethod.OPTIONS;
        case "HEAD":
            return HttpMethod.HEAD;
        default:
            return HttpMethod.OTHER;
        }
    }

    private HttpResponse.RequestType getRequestType(HttpServletRequest request, Response response) {
        if (response.getRequestType() != null) return response.getRequestType();

        // Deduce defaults from path and method:
        String path = request.getRequestURI();
        for (String monitoringHandlerPath : monitoringHandlerPaths) {
            if (path.startsWith(monitoringHandlerPath)) return Response.RequestType.MONITORING;
        }
        for (String searchHandlerPath : searchHandlerPaths) {
            if (path.startsWith(searchHandlerPath)) return Response.RequestType.READ;
        }
        if ("GET".equals(request.getMethod())) {
            return Response.RequestType.READ;
        } else {
            return Response.RequestType.WRITE;
        }
    }

    public List<StatisticsEntry> takeStatistics() {
        var ret = new ArrayList<StatisticsEntry>();
        for (HttpScheme scheme : HttpScheme.values()) {
            int schemeIndex = scheme.ordinal();
            for (HttpMethod method : HttpMethod.values()) {
                int methodIndex = method.ordinal();
                for (int group = 0; group < HTTP_RESPONSE_GROUPS.length; group++) {
                    for (Response.RequestType type : Response.RequestType.values()) {
                        long value = statistics[schemeIndex][methodIndex][group][type.ordinal()].sumThenReset();
                        if (value > 0) {
                            ret.add(new StatisticsEntry(scheme.name().toLowerCase(), method.name(), HTTP_RESPONSE_GROUPS[group], type.name().toLowerCase(), value));
                        }
                    }
                }
            }
        }
        return ret;
    }

    public static class StatisticsEntry {

        public final String scheme;
        public final String method;
        public final String name;
        public final String requestType;
        public final long value;

        public StatisticsEntry(String scheme, String method, String name, String requestType, long value) {
            this.scheme = scheme;
            this.method = method;
            this.name = name;
            this.requestType = requestType;
            this.value = value;
        }

        @Override
        public String toString() {
            return "scheme: " + scheme +
                   ", method: " + method +
                   ", name: " + name +
                   ", requestType: " + requestType +
                   ", value: " + value;
        }

    }

}
