// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategyFactory;
import com.yahoo.vespa.jaxrs.client.JerseyJaxRsClientFactory;
import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.applicationmodel.HostName;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author stiankri
 * @author bakksjo
 */
public class OrchestratorImpl implements Orchestrator {
    private static final Logger logger = Logger.getLogger(OrchestratorImpl.class.getName());
    // TODO: Figure out the port dynamically.
    private static final int HARDCODED_ORCHESTRATOR_PORT = 19071;
    // TODO: Find a way to avoid duplicating this (present in orchestrator's services.xml also).
    private static final String ORCHESTRATOR_PATH_PREFIX = "/orchestrator";
    private static final String ORCHESTRATOR_PATH_PREFIX_HOST_API
            = ORCHESTRATOR_PATH_PREFIX + HostApi.PATH_PREFIX;

    // We use this to allow client code to treat resume() calls as idempotent and cheap,
    // but we actually filter out redundant resume calls to orchestrator.
    private final Set<HostName> resumedHosts = new HashSet<>();

    private final JaxRsStrategy<HostApi> hostApiClient;

    public OrchestratorImpl(JaxRsStrategy<HostApi> hostApiClient) {
        this.hostApiClient = hostApiClient;
    }

    @Override
    public boolean suspend(final HostName hostName) {
        resumedHosts.remove(hostName);
        try {
            return hostApiClient.apply(api -> {
                final UpdateHostResponse response = api.suspend(hostName.s());
                return response.reason() == null;
            });
        } catch (ClientErrorException e) {
            if (e instanceof NotFoundException || e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                // Orchestrator doesn't care about this node, so don't let that stop us.
                return true;
            }
            logger.log(Level.INFO, "Orchestrator rejected suspend request for host " + hostName, e);
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to communicate with orchestrator", e);
            return false;
        }
    }

    @Override
    public boolean resume(final HostName hostName) {
        if (resumedHosts.contains(hostName)) {
            return true;
        }

        try {
            final boolean resumeSucceeded = hostApiClient.apply(api -> {
                final UpdateHostResponse response = api.resume(hostName.s());
                return response.reason() == null;
            });
            if (resumeSucceeded) {
                resumedHosts.add(hostName);
            }
            return resumeSucceeded;
        } catch (ClientErrorException e) {
            logger.log(Level.INFO, "Orchestrator rejected resume request for host " + hostName, e);
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to communicate with orchestrator", e);
            return false;
        }
    }

    public static JaxRsStrategy<HostApi> makeOrchestratorHostApiClient() {
        final Set<HostName> configServerHosts = Environment.getConfigServerHostsFromYinstSetting();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Emnvironment setting for config servers missing or empty.");
        }
        final JaxRsClientFactory jaxRsClientFactory = new JerseyJaxRsClientFactory();
        final JaxRsStrategyFactory jaxRsStrategyFactory = new JaxRsStrategyFactory(
                configServerHosts, HARDCODED_ORCHESTRATOR_PORT, jaxRsClientFactory);
        return jaxRsStrategyFactory.apiWithRetries(HostApi.class, ORCHESTRATOR_PATH_PREFIX_HOST_API);
    }

}
