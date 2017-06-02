// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.DockerTestUtils;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * <pre>
 * Requires docker daemon, see {@link com.yahoo.vespa.hosted.dockerapi.DockerTestUtils} for more details.
 *
 * Issues:
 *  1. If you cannot make Docker Toolbox start, try starting Virtualbox and turn off the "default" machine
 *  2. If the above is not enough try "sudo ifconfig vboxnet0 down && sudo ifconfig vboxnet0 up" (see https://github.com/docker/kitematic/issues/1193)
 * </pre>
 *
 * @author freva
 */
public class RunVespaLocal {
    private static final Environment environment = new Environment.Builder()
            .configServerHosts(LocalZoneUtils.CONFIG_SERVER_HOSTNAME)
            .environment("dev")
            .region("vespa-local")
            .parentHostHostname(HostName.getLocalhost())
            .inetAddressResolver(new InetAddressResolver())
            .build();

    private final Logger logger = Logger.getLogger("RunVespaLocal");
    private final Docker docker;
    private final Path pathToVespaRoot;

    public RunVespaLocal(Path pathToVespaRoot) {
        this.docker = DockerTestUtils.getDocker();
        this.pathToVespaRoot = pathToVespaRoot;
    }


    /**
     * Starts config server, provisions numNodesToProvision and puts them in ready state
     */
    public void startLocalZoneWithNodes(DockerImage dockerImage, int numNodesToProvision) throws IOException {
        logger.info("Starting config-server");
        LocalZoneUtils.startConfigServerIfNeeded(docker, environment, dockerImage, pathToVespaRoot);

        logger.info("Waiting until config-server is ready to serve");
        URL configServerUrl = new URL("http://" + LocalZoneUtils.CONFIG_SERVER_HOSTNAME +
                ":" + LocalZoneUtils.CONFIG_SERVER_WEB_SERVICE_PORT + "/state/v1/health");
        assertTrue("Could not start config server", LocalZoneUtils.isReachableURL(configServerUrl, Duration.ofSeconds(120)));

        logger.info("Provisioning nodes");
        Set<String> hostnames = LocalZoneUtils.provisionNodes(LocalZoneUtils.NODE_ADMIN_HOSTNAME, numNodesToProvision);
        hostnames.stream()
                .map(LocalZoneUtils::getContainerNodeSpec)
                .flatMap(optional -> optional.map(Stream::of).orElseGet(Stream::empty)) // Remove with JDK 9
                .forEach(nodeSpec -> {
                    if (nodeSpec.nodeState == Node.State.provisioned) LocalZoneUtils.setState(Node.State.dirty, nodeSpec.hostname);
                    if (nodeSpec.nodeState == Node.State.dirty) LocalZoneUtils.setState(Node.State.ready, nodeSpec.hostname);
                });
    }

    /**
     * Starts node-admin inside a container
     * @param dockerImage            Docker image that node-admin should be running
     * @param pathToContainerStorage Path to where the container data will be stored, the path must exist and must
     *                               be writeable by user, normally /home/docker/container-storage
     */
    public void startNodeAdminAsContainer(DockerImage dockerImage, Path pathToContainerStorage) throws IOException {
        logger.info("Starting node-admin");
        String parentHostHostname = LocalZoneUtils.NODE_ADMIN_HOSTNAME;
        LocalZoneUtils.startNodeAdminIfNeeded(docker, environment, dockerImage, pathToContainerStorage);

        logger.info("Provisioning host at " + parentHostHostname);
        LocalZoneUtils.provisionHost(parentHostHostname);
        LocalZoneUtils.getContainerNodeSpec(parentHostHostname)
                .ifPresent(nodeSpec -> {
                    if (nodeSpec.nodeState == Node.State.provisioned) {
                        LocalZoneUtils.setState(Node.State.dirty, nodeSpec.hostname);
                        LocalZoneUtils.setState(Node.State.ready, nodeSpec.hostname);
                    }
                });

        logger.info("Deploying node-admin app");
        Path pathToNodeAdminApp = pathToVespaRoot.resolve("node-admin/node-admin-zone-app");
        Path pathToNodeAdminAppComponents = pathToNodeAdminApp.resolve("components");
        Files.createDirectories(pathToNodeAdminAppComponents);
        Path[] appComponents = {pathToVespaRoot.resolve("node-admin/target/node-admin-jar-with-dependencies.jar"),
                pathToVespaRoot.resolve("docker-api/target/docker-api-jar-with-dependencies.jar")};

        for (Path path : appComponents) {
            Files.copy(path, pathToNodeAdminAppComponents.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }

        LocalZoneUtils.deployApp(docker, pathToNodeAdminApp, "vespa", "node-admin");

        logger.info("Waiting for node-admin to serve");
        try {
            URL nodeUrl = new URL("http://localhost:" + System.getenv("VESPA_WEB_SERVICE_PORT") + "/");
            assertTrue(LocalZoneUtils.isReachableURL(nodeUrl, Duration.ofSeconds(120)));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Packages, deploys an app and waits for the node to come up
     * @param pathToApp Path to the directory of the application to deploy
     */
    public void deployApplication(Path pathToApp) {
        logger.info("Packaging application");
        LocalZoneUtils.packageApp(pathToApp);
        logger.info("Deploying application");
        LocalZoneUtils.deployApp(docker, pathToApp.resolve("target/application.zip"));

        Set<String> containers = LocalZoneUtils.getContainersForApp();
        try {
            URL nodeUrl = new URL("http://" + containers.iterator().next() + ":" + System.getenv("VESPA_WEB_SERVICE_PORT") + "/");
            assertTrue(LocalZoneUtils.isReachableURL(nodeUrl, Duration.ofMinutes(3)));
            logger.info("Endpoint " + nodeUrl + " is now ready");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void deleteApplication() {
        logger.info("Deleting application");
        LocalZoneUtils.deleteApplication();
    }
}
