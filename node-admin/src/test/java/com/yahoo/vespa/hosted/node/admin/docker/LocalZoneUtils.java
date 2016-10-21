// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.DockerImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author freva
 */
public class LocalZoneUtils {
    public static final String CONFIG_SERVER_HOSTNAME = "config-server";
    public static final DockerImage VESPA_LOCAL_IMAGE = new DockerImage("vespa-local:latest");

    private static final String VESPA_HOME = "/home/y";
    private static final String APP_HOSTNAME_PREFIX = "cnode-";

    public static boolean startConfigServer(Docker docker, Environment environment) throws UnknownHostException {
        ContainerName confingServerContainerName = new ContainerName(CONFIG_SERVER_HOSTNAME);
        docker.createContainerCommand(VESPA_LOCAL_IMAGE, confingServerContainerName, CONFIG_SERVER_HOSTNAME)
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withIpAddress(environment.getInetAddressForHost(CONFIG_SERVER_HOSTNAME))
                .withVolume("/etc/hosts", "/etc/hosts")
                .withEnvironment("HOSTED_VESPA_ENVIRONMENT", environment.getEnvironment())
                .withEnvironment("HOSTED_VESPA_REGION", environment.getRegion())
                .withEnvironment("CONFIG_SERVER_HOSTNAME", CONFIG_SERVER_HOSTNAME)
                .withEnvironment("VESPA_HOME", VESPA_HOME)
                .withEntrypoint("/usr/local/bin/start-config-server.sh")
                .withUlimit("nofile", 16384, 16384)
                .withUlimit("nproc", 409600, 409600)
                .withUlimit("core", -1, -1)
                .create();

        docker.startContainer(confingServerContainerName);

        try {
            URL url = new URL("http://" + CONFIG_SERVER_HOSTNAME + ":4080/state/v1/health");
            for (int i = 0; i < 10; i++) {
                Thread.sleep(50);
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                if (http.getResponseCode() == 200) return true;
            }
        } catch (IOException | InterruptedException ignored) { }

        return false;
    }

    public static void buildVespaLocalDockerImage(Docker docker, DockerImage vespaBaseImage) throws IOException {
        Path dockerfileTemplatePath = Paths.get("Dockerfile.template");
        Path dockerfilePath = Paths.get("Dockerfile");

        String dockerfileTemplate = new String(Files.readAllBytes(dockerfileTemplatePath))
                .replaceAll("\\$NODE_ADMIN_FROM_IMAGE", vespaBaseImage.asString())
                .replaceAll("\\$VESPA_HOME", VESPA_HOME);

        Files.write(dockerfilePath, dockerfileTemplate.getBytes());

        docker.buildImage(dockerfilePath.toAbsolutePath().getParent().toFile(), VESPA_LOCAL_IMAGE);
    }
}

