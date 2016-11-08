// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.DockerImpl;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
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
import java.nio.file.StandardCopyOption;
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
    private static final String APPLICATION_NAME = "default";

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

        int maxRetries = 2000;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (i % 100 == 0) System.out.println("Check if config server is up, try " + i + " of " + maxRetries);

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

        String dockerfileTemplate = new String(Files.readAllBytes(dockerfileTemplatePath))
                .replaceAll("\\$NODE_ADMIN_FROM_IMAGE", vespaBaseImage.asString())
                .replaceAll("\\$VESPA_HOME", Defaults.getDefaults().vespaHome());

        /*
         * Because the daemon could be running on a remote machine, docker build command will upload the entire
         * build path to daemon and then execute the Dockerfile. This means that:
         * 1. We cant use relative paths in Dockerfile
         * 2. We should avoid using a large directory as build root
         *
         * Therefore, copy docker-api jar to node-admin/target and used node-admin as build root instead of vespa/
          */
        Path projectRoot = Paths.get("").toAbsolutePath().getParent();
        Files.copy(projectRoot.resolve("docker-api/target/docker-api-jar-with-dependencies.jar"),
                projectRoot.resolve("node-admin/target/docker-api-jar-with-dependencies.jar"),
                StandardCopyOption.REPLACE_EXISTING);

        Path dockerfilePath = Paths.get("Dockerfile").toAbsolutePath();

        Files.write(dockerfilePath, dockerfileTemplate.getBytes());
        docker.buildImage(dockerfilePath.getParent().toFile(), VESPA_LOCAL_IMAGE);
    }

    /**
     * Adds numberOfNodes to node-repo and returns a set of node hostnames.
     */
    public static Set<String> provisionNodes(String parentHostname, int numberOfNodes) {
        Set<String> hostnames = new HashSet<>();
        List<Map<String, String>> nodesToAdd = new ArrayList<>();
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

    public static void deployApp(Docker docker, Path pathToApp) {
        deployApp(docker, pathToApp, TENANT_NAME, APPLICATION_NAME);
    }

    public static void deployApp(Docker docker, Path pathToApp, String tenantName, String applicationName) {
        Path pathToAppOnConfigServer = Paths.get("/tmp");
        docker.copyArchiveToContainer(pathToApp.toAbsolutePath().toString(),
                CONFIG_SERVER_CONTAINER_NAME, pathToAppOnConfigServer.toString());

        try { // Add tenant, ignore exception if tenant already exists
            requestExecutor.put("/application/v2/tenant/" + tenantName, CONFIG_SERVER_WEB_SERVICE_PORT, Optional.empty(), Map.class);
        } catch (RuntimeException e) {
            if (! e.getMessage().contains("There already exists a tenant '" + tenantName)) {
                throw e;
            }
        }
        System.out.println("prepare " + applicationName);
        final String deployPath = Defaults.getDefaults().underVespaHome("bin/deploy");
        ProcessResult copyProcess = docker.executeInContainer(CONFIG_SERVER_CONTAINER_NAME, deployPath, "-e",
                tenantName, "-a", applicationName, "prepare", pathToAppOnConfigServer.resolve(pathToApp.getFileName()).toString());
        if (! copyProcess.isSuccess()) {
            throw new RuntimeException("Could not prepare " + pathToApp + " on " + CONFIG_SERVER_CONTAINER_NAME.asString() +
                    "\n" + copyProcess.getOutput() + "\n" + copyProcess.getErrors());
        }

        System.out.println("activate " + applicationName);
        ProcessResult execProcess = docker.executeInContainer(CONFIG_SERVER_CONTAINER_NAME, deployPath, "-e",
                tenantName, "-a", applicationName, "activate");
        if (! execProcess.isSuccess()) {
            throw new RuntimeException("Could not activate application\n" + copyProcess.getOutput() + "\n" + copyProcess.getErrors());
        }
    }

    public static void deleteApplication() {
        deleteApplication(TENANT_NAME, APPLICATION_NAME);
    }

    public static void deleteApplication(String tenantName, String appName) {
        requestExecutor.delete("/application/v2/tenant/" + tenantName + "/application/" + appName,
                CONFIG_SERVER_WEB_SERVICE_PORT, Map.class);
    }
}

