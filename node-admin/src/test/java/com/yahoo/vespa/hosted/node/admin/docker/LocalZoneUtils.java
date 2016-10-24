// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.*;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author freva
 */
public class LocalZoneUtils {
    public static final String CONFIG_SERVER_HOSTNAME = "config-server";
    public static final ContainerName CONFIG_SERVER_CONTAINER_NAME = new ContainerName(CONFIG_SERVER_HOSTNAME);
    public static final int CONFIG_SERVER_WEB_SERVICE_PORT = 4080;
    public static final DockerImage VESPA_LOCAL_IMAGE = new DockerImage("vespa-local:latest");

    private static final ConfigServerHttpRequestExecutor requestExecutor = ConfigServerHttpRequestExecutor.create(
            Collections.singleton(CONFIG_SERVER_HOSTNAME));
    private static final String APP_HOSTNAME_PREFIX = "cnode-";
    private static final String TENANT_NAME = "localtenant";

    public static boolean startConfigServerIfNeeded(Docker docker, Environment environment) throws UnknownHostException {
        Optional<Container> container = docker.getContainer(CONFIG_SERVER_HOSTNAME);
        if (container.isPresent()) {
            if (container.get().isRunning) return true;
            else docker.deleteContainer(CONFIG_SERVER_CONTAINER_NAME);
        }

        docker.createContainerCommand(VESPA_LOCAL_IMAGE, CONFIG_SERVER_CONTAINER_NAME, CONFIG_SERVER_HOSTNAME)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withIpAddress(environment.getInetAddressForHost(CONFIG_SERVER_HOSTNAME))
                .withVolume("/etc/hosts", "/etc/hosts")
                .withEnvironment("HOSTED_VESPA_ENVIRONMENT", environment.getEnvironment())
                .withEnvironment("HOSTED_VESPA_REGION", environment.getRegion())
                .withEnvironment("CONFIG_SERVER_HOSTNAME", CONFIG_SERVER_HOSTNAME)
                .withEntrypoint(Defaults.getDefaults().underVespaHome("bin/start-config-server.sh"))
                .withUlimit("nofile", 16384, 16384)
                .withUlimit("nproc", 409600, 409600)
                .withUlimit("core", -1, -1)
                .create();

        docker.startContainer(CONFIG_SERVER_CONTAINER_NAME);

        for (int i = 0; i < 500; i++) {
            try {
                URL url = new URL("http://" + CONFIG_SERVER_HOSTNAME + ":" + CONFIG_SERVER_WEB_SERVICE_PORT +
                        "/state/v1/health");
                Thread.sleep(100);
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                if (http.getResponseCode() == 200) return true;
            } catch (IOException | InterruptedException ignored) { }
        }

        return false;
    }

    public static void buildVespaLocalDockerImage(Docker docker, DockerImage vespaBaseImage) throws IOException {
        Path dockerfileTemplatePath = Paths.get("Dockerfile.template");
        Path dockerfilePath = Paths.get("Dockerfile");

        String dockerfileTemplate = new String(Files.readAllBytes(dockerfileTemplatePath))
                .replaceAll("\\$NODE_ADMIN_FROM_IMAGE", vespaBaseImage.asString())
                .replaceAll("\\$VESPA_HOME", Defaults.getDefaults().vespaHome());

        Files.write(dockerfilePath, dockerfileTemplate.getBytes());

        docker.buildImage(dockerfilePath.toAbsolutePath().getParent().toFile(), VESPA_LOCAL_IMAGE);
    }

    /**
     * Adds numberOfNodes to node-repo and returns a set of node hostnames.
     */
    public static Set<String> provisionNodes(String parentHostname, int numberOfNodes) {
        Set<String> hostnames = new HashSet<>();
        List<Map> nodesToAdd = new ArrayList<>();
        for (int i = 1; i <= numberOfNodes; i++) {
            final String hostname = APP_HOSTNAME_PREFIX + i;
            Map<String, String> provisionNodeRequest = new HashMap<>();
            provisionNodeRequest.put("parentHostname", parentHostname);
            provisionNodeRequest.put("type", "tenant");
            provisionNodeRequest.put("flavor", "docker");
            provisionNodeRequest.put("hostname", hostname);
            provisionNodeRequest.put("openStackId", "fake-" + hostname);
            nodesToAdd.add(provisionNodeRequest);
            hostnames.add(hostname);
        }

        requestExecutor.post("/nodes/v2/node", CONFIG_SERVER_WEB_SERVICE_PORT, nodesToAdd, Map.class);
        return hostnames;
    }

    public static void setState(Node.State state, String hostname) {
        requestExecutor.put("/nodes/v2/state/" + state + "/" + hostname,
                CONFIG_SERVER_WEB_SERVICE_PORT, Optional.empty(), Map.class);
    }

    public static void prepareAppForDeployment(Docker docker, Path pathToApp) {
        Path pathToAppOnConfigServer = Paths.get("/tmp");
        docker.copyArchiveToContainer(pathToApp.toAbsolutePath().toString(),
                CONFIG_SERVER_CONTAINER_NAME, pathToAppOnConfigServer.toString());

        try { // Add tenant, ignore exception if tenant already exists
            requestExecutor.put("/application/v2/tenant/" + TENANT_NAME, CONFIG_SERVER_WEB_SERVICE_PORT, Optional.empty(), Map.class);
        } catch (RuntimeException e) {
            if (! e.getMessage().contains("There already exists a tenant '" + TENANT_NAME)) {
                throw e;
            }
        }

        final String deployPath = Defaults.getDefaults().underVespaHome("bin/deploy");
        ProcessResult copyProcess = docker.executeInContainer(CONFIG_SERVER_CONTAINER_NAME, deployPath, "-e",
                TENANT_NAME, "prepare", pathToAppOnConfigServer.resolve(pathToApp.getFileName()).toString());
        if (! copyProcess.isSuccess()) {
            throw new RuntimeException("Could not copy " + pathToApp + " to " + CONFIG_SERVER_CONTAINER_NAME.asString() +
                    "\n" + copyProcess.getErrors());
        }

        ProcessResult execProcess = docker.executeInContainer(CONFIG_SERVER_CONTAINER_NAME, deployPath, "-e",
                TENANT_NAME, "activate");
        if (! execProcess.isSuccess()) {
            throw new RuntimeException("Could not activate application\n" + execProcess.getErrors());
        }
    }
}

