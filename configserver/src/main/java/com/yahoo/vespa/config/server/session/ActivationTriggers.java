package com.yahoo.vespa.config.server.session;

import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;

import java.util.List;

/**
 * Events that should trigger upon activation of a session.
 * A prepared session is always prepared with a specific origin session, unless it is the first session in a zone,
 * and activation of a session fails if the current session changes before the prepared session is activated.
 * Therefore, the trigger events computed upon preparation of a session will always be valid when a session is activated,
 * and can be stored as part of the session state.
 * This is needed to properly handle activation of sessions on different servers than the preparing ones.
 *
 * @author jonmv
 */
public record ActivationTriggers(List<NodeRestart> nodeRestarts, List<Reindexing> reindexings) {

    private static final ActivationTriggers empty = new ActivationTriggers(List.of(), List.of());

    public record NodeRestart(String hostname) { }
    public record Reindexing(String clusterId, String documentType) { }

    public static ActivationTriggers empty() { return empty; }

    public static ActivationTriggers from(ConfigChangeActions configChangeActions, boolean isInternalRedeployment) {
        return new ActivationTriggers(configChangeActions.getRestartActions()
                                                         .useForInternalRestart(isInternalRedeployment)
                                                         .hostnames().stream()
                                                         .map(NodeRestart::new)
                                                         .toList(),
                                      configChangeActions.getReindexActions().getEntries().stream()
                                                         .map(entry -> new Reindexing(entry.getClusterName(), entry.getDocumentType()))
                                                         .toList());
    }

}
