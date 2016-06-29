package com.yahoo.vespa.hosted.node.admin.provider;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeMechanisms;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.util.Set;
import java.util.function.Function;

/**
 * Set up node admin for production.
 *
 * @author dybis
 */
public class ComponentsProviderImpl implements ComponentsProvider {

    private final Docker docker;
    private static final long INITIAL_SCHEDULER_DELAY_MILLIS = 0;
    private static final long INTERVAL_SCHEDULER_IN_MILLIS = 60000;

    private static final int HARDCODED_NODEREPOSITORY_PORT = 19071;
    private static final String ENV_HOSTNAME = "HOSTNAME";
    public ComponentsProviderImpl(final Docker docker) {
        this.docker = docker;
    }

    @Override
    public NodeAdmin.NodeAdminStateUpdater getNodeAdminStateUpdater() {
        String baseHostName = java.util.Optional.ofNullable(System.getenv(ENV_HOSTNAME))
                .orElseThrow(() -> new IllegalStateException("Environment variable " + ENV_HOSTNAME + " unset"));

        Set<HostName> configServerHosts = Environment.getConfigServerHosts();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }

        NodeRepository nodeRepository = new NodeRepositoryImpl(configServerHosts, HARDCODED_NODEREPOSITORY_PORT, baseHostName);

        Orchestrator orchestrator = OrchestratorImpl.createOrchestratorFromSettings();
        final Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, nodeRepository, orchestrator, new NodeMechanisms(docker));
        final NodeAdmin nodeAdmin = new NodeAdmin.NodeAdminImpl(docker, nodeAgentFactory);
        return new NodeAdmin.NodeAdminStateUpdater(
                nodeRepository, nodeAdmin, INITIAL_SCHEDULER_DELAY_MILLIS, INTERVAL_SCHEDULER_IN_MILLIS, orchestrator, baseHostName);
    }
}
