// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.rule;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.config.jdisc.http.filter.RuleBasedFilterConfig;
import com.yahoo.vespa.config.jdisc.http.filter.RuleBasedFilterConfig.Rule.Action;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Security request filter that filters requests based on host, method and uri path.
 *
 * @author bjorncs
 */
public class RuleBasedRequestFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(RuleBasedRequestFilter.class.getName());

    private final Metric metric;
    private final boolean dryrun;
    private final List<Rule> rules;
    private final ErrorResponse defaultResponse;

    @Inject
    public RuleBasedRequestFilter(Metric metric, RuleBasedFilterConfig config) {
        this.metric = metric;
        this.dryrun = config.dryrun();
        this.rules = Rule.fromConfig(config.rule());
        this.defaultResponse = createDefaultResponse(config.defaultRule());
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        String method = request.getMethod();
        URI uri = request.getUri();
        for (Rule rule : rules) {
            if (rule.matches(method, uri)) {
                log.log(Level.FINE, () ->
                        String.format("Request '%h' with method '%s' and uri '%s' matched rule '%s'", request, method, uri, rule.name));
                return responseFor(request, rule.name, rule.response);
            }
        }
        return responseFor(request, "default", defaultResponse);
    }

    private static ErrorResponse createDefaultResponse(RuleBasedFilterConfig.DefaultRule defaultRule) {
        switch (defaultRule.action()) {
            case ALLOW: return null;
            case BLOCK: {
                Response response = new Response(defaultRule.blockResponseCode());
                defaultRule.blockResponseHeaders().forEach(h -> response.headers().add(h.name(), h.value()));
                return new ErrorResponse(response, defaultRule.blockResponseMessage());
            }
            default: throw new IllegalArgumentException(defaultRule.action().name());
        }
    }

    private Optional<ErrorResponse> responseFor(DiscFilterRequest request, String ruleName, ErrorResponse response) {
        int statusCode = response != null ? response.getResponse().getStatus() : 0;
        Metric.Context metricContext = metric.createContext(Map.of(
                "rule", ruleName,
                "dryrun", Boolean.toString(dryrun),
                "statusCode", Integer.toString(statusCode)));
        if (response != null) {
            metric.add("jdisc.http.filter.rule.blocked_requests", 1L, metricContext);
            log.log(Level.FINE, () -> String.format(
                    "Blocking request '%h' with status code '%d' using rule '%s' (dryrun=%b)", request, statusCode, ruleName, dryrun));
            return dryrun ? Optional.empty() : Optional.of(response);
        } else {
            metric.add("jdisc.http.filter.rule.allowed_requests", 1L, metricContext);
            log.log(Level.FINE, () -> String.format("Allowing request '%h' using rule '%s' (dryrun=%b)", request, ruleName, dryrun));
            return Optional.empty();
        }
    }

    private static class Rule {

        final String name;
        final Set<String> hostnames;
        final Set<String> methods;
        final Set<String> pathGlobExpressions;
        final ErrorResponse response;

        static List<Rule> fromConfig(List<RuleBasedFilterConfig.Rule> config) {
            return config.stream()
                    .map(Rule::new)
                    .collect(Collectors.toList());
        }

        Rule(RuleBasedFilterConfig.Rule config) {
            this.name = config.name();
            this.hostnames = Set.copyOf(config.hostNames());
            this.methods = config.methods().stream()
                    .map(m -> m.name().toUpperCase())
                    .collect(Collectors.toSet());
            this.pathGlobExpressions = Set.copyOf(config.pathExpressions());
            this.response = config.action() == Action.Enum.BLOCK ? createResponse(config) : null;
        }

        private static ErrorResponse createResponse(RuleBasedFilterConfig.Rule config) {
            Response response = new Response(config.blockResponseCode());
            config.blockResponseHeaders().forEach(h -> response.headers().add(h.name(), h.value()));
            return new ErrorResponse(response, config.blockResponseMessage());
        }

        boolean matches(String method, URI uri) {
            boolean methodMatches = methods.isEmpty() || methods.contains(method.toUpperCase());
            String host = uri.getHost();
            boolean hostnameMatches = hostnames.isEmpty() || (host != null && hostnames.contains(host));
            // Path segments cannot be validated in this filter, as we don't know what API it protects.
            // Specifically, /document/v1 must allow _any_ rest path segment, as there is no restriction on document IDs.
            boolean pathMatches = pathGlobExpressions.isEmpty() || pathGlobExpressions.stream().anyMatch(Path.withoutValidation(uri)::matches);
            return methodMatches && hostnameMatches && pathMatches;
        }

    }
}
