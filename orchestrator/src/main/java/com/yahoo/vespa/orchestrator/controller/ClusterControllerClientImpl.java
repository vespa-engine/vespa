// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import ai.vespa.hosted.client.HttpClient;
import ai.vespa.hosted.client.HttpClient.HostStrategy;
import ai.vespa.hosted.client.HttpClient.ResponseException;
import ai.vespa.hosted.client.HttpClient.ResponseVerifier;
import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Path;
import ai.vespa.http.HttpURL.Query;
import ai.vespa.http.HttpURL.Scheme;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Method;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Default implementation of the ClusterControllerClient.
 *
 * @author smorgrav
 */
public class ClusterControllerClientImpl implements ClusterControllerClient {

    enum Condition {
        FORCE, SAFE;
    }

    public static final String REQUEST_REASON = "Orchestrator";

    private final HttpClient client;
    private final List<HostName> hosts;
    private final String clusterName;

    ClusterControllerClientImpl(HttpClient client, List<HostName> hosts, String clusterName) {
        this.clusterName = clusterName;
        this.hosts = hosts;
        this.client = client;
    }

    @Override
    public boolean setNodeState(OrchestratorContext context, HostName host, int storageNodeIndex, ClusterControllerNodeState wantedState) {
        try {
            Inspector response = client.send(strategyWithTimeout(hosts, context.getClusterControllerTimeouts()), Method.POST)
                                       .at("cluster", "v2", clusterName, "storage", Integer.toString(storageNodeIndex))
                                       .deadline(context.getClusterControllerTimeouts().readBudget())
                                       .body(stateChangeRequestBytes(wantedState, Condition.SAFE, context.isProbe()))
                                       .throwing(retryOnRedirect)
                                       .read(SlimeUtils::jsonToSlime).get();
            if ( ! response.field("wasModified").asBool()) {
                if (context.isProbe())
                    return false;

                throw new HostStateChangeDeniedException(host,
                                                         HostedVespaPolicy.SET_NODE_STATE_CONSTRAINT,
                                                         "Failed to set state to " + wantedState +
                                                         " in cluster controller: " + response.field("reason").asString());
            }
            return true;
        }
        catch (ResponseException e) {
            throw new HostStateChangeDeniedException(host,
                                                     HostedVespaPolicy.SET_NODE_STATE_CONSTRAINT,
                                                     "Failed setting node " + storageNodeIndex + " in cluster " +
                                                     clusterName + " to state " + wantedState + ": " + e.getMessage());
        }
        catch (UncheckedIOException e) {
            throw new HostStateChangeDeniedException(host,
                                                     HostedVespaPolicy.CLUSTER_CONTROLLER_AVAILABLE_CONSTRAINT,
                                                     String.format("Giving up setting %s for storage node with index %d in cluster %s: %s",
                                                                   wantedState,
                                                                   storageNodeIndex,
                                                                   clusterName,
                                                                   e.getMessage()),
                                                     e.getCause());
        }
        catch (UncheckedTimeoutException e) {
            throw new HostStateChangeDeniedException(host,
                                                     HostedVespaPolicy.DEADLINE_CONSTRAINT,
                                                     "Timeout while waiting for setNodeState(" + storageNodeIndex + ", " + wantedState +
                                                     ") against " + hosts + ": " + e.getMessage(),
                                                     e);
        }
    }

    @Override
    public void setApplicationState(OrchestratorContext context, ApplicationInstanceId applicationId,
                                    ClusterControllerNodeState wantedState) throws ApplicationStateChangeDeniedException {
        try {
            Inspector response = client.send(strategyWithTimeout(hosts, context.getClusterControllerTimeouts()), Method.POST)
                                       .at("cluster", "v2", clusterName)
                                       .deadline(context.getClusterControllerTimeouts().readBudget())
                                       .body(stateChangeRequestBytes(wantedState, Condition.FORCE, false))
                                       .throwing(retryOnRedirect)
                                       .read(SlimeUtils::jsonToSlime).get();
            if ( ! response.field("wasModified").asBool()) {
                throw new ApplicationStateChangeDeniedException("Failed to set application " + applicationId + ", cluster name " +
                                                                clusterName + " to cluster state " + wantedState + " due to: " +
                                                                response.field("reason").asString());
            }
        }
        catch (ResponseException e) {
            throw new ApplicationStateChangeDeniedException("Failed to set application " + applicationId + " cluster name " +
                                                            clusterName + " to cluster state " + wantedState + " due to: " + e.getMessage());
        }
        catch (UncheckedIOException e) {
            throw new ApplicationStateChangeDeniedException("Failed communicating with cluster controllers " + hosts +
                                                            " with cluster ID " + clusterName + ": " + e.getCause().getMessage());
        }
        catch (UncheckedTimeoutException e) {
            throw new ApplicationStateChangeDeniedException("Timed out while waiting for cluster controllers " + hosts +
                                                            " with cluster ID " + clusterName + ": " + e.getMessage());
        }
    }

    static byte[] stateChangeRequestBytes(ClusterControllerNodeState wantedState, Condition condition, boolean isProbe) {
        Cursor root = new Slime().setObject();
        Cursor stateObject = root.setObject("user");
        stateObject.setString("reason", REQUEST_REASON);
        stateObject.setString("state", wantedState.getWireName());
        root.setString("condition", condition.name());
        if (isProbe) root.setBool("probe", true);
        return Exceptions.uncheck(() -> SlimeUtils.toJsonBytes(root));
    }

    /** ᕙ༼◕_◕༽ᕤ hack to vary query parameters with retries ᕙ༼◕_◕༽ᕤ */
    static HostStrategy strategyWithTimeout(List<HostName> hosts, ClusterControllerClientTimeouts timeouts) {
        return () -> new Iterator<>() {
            final Iterator<HostName> wrapped = hosts.iterator();
            @Override public boolean hasNext() {
                return wrapped.hasNext();
            }
            @Override public URI next() {
                return HttpURL.create(Scheme.http,
                                      DomainName.of(wrapped.next().s()),
                                      19050,
                                      Path.empty(),
                                      Query.empty().set("timeout", Double.toString(timeouts.getServerTimeoutOrThrow().toMillis() * 1e-3)))
                              .asURI();
            }
        };
    }

    static final ResponseVerifier retryOnRedirect = new ResponseVerifier() {
        @Override
        public boolean shouldRetry(int statusCode) { // Need to try the other servers when we get a redirect.
            return statusCode < 400 || statusCode == 503;
        }
        @Override
        public RuntimeException toException(int statusCode, byte[] body, ClassicHttpRequest request) {
            Inspector root = SlimeUtils.jsonToSlime(body).get();
            String detail = root.field("message").valid() ? root.field("message").asString()
                                                          : new String(body, UTF_8);
            return new ResponseException("got status code " + statusCode + " for " + request + (detail.isBlank() ? "" : ": " + detail));
        }
    };

}
