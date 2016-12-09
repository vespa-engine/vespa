// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.DockerTestUtils;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.integrationTests.CallOrderVerifier;
import com.yahoo.vespa.hosted.node.admin.integrationTests.StorageMaintainerMock;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.provider.ComponentsProviderImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

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
    private static final Environment environment = new Environment(
            Collections.singleton(LocalZoneUtils.CONFIG_SERVER_HOSTNAME), "prod", "vespa-local",
            HostName.getLocalhost(), new InetAddressResolver());

    private NodeAdminStateUpdater nodeAdminStateUpdater = null;
    private final Docker docker;
    private final Logger logger = Logger.getLogger("RunVespaLocal");

    RunVespaLocal() {
        this.docker = DockerTestUtils.getDocker();
    }

    /**
     * Pulls the base image and builds the vespa-local image
     * @param vespaBaseImage Vespa docker image to use as base for the image that the config-server and nodes will run
     */
    void buildVespaLocalImage(DockerImage vespaBaseImage) throws ExecutionException, InterruptedException, IOException {
        if (!docker.imageIsDownloaded(vespaBaseImage)) {
            logger.info("Pulling " + vespaBaseImage.asString() + " (This may take a while)");
            docker.pullImageAsync(vespaBaseImage).get();
        }

        logger.info("Building " + LocalZoneUtils.VESPA_LOCAL_IMAGE.asString());
        LocalZoneUtils.buildVespaLocalDockerImage(docker, vespaBaseImage);
    }

    /**
     * Starts config server, provisions numNodesToProvision and puts them in ready state
     */
    void startLocalZoneWithNodes(int numNodesToProvision) throws IOException, InterruptedException, ExecutionException {
        logger.info("Starting config-server");
        LocalZoneUtils.startConfigServerIfNeeded(docker, environment);

        logger.info("Waiting until config-server is ready to serve");
        URL configServerUrl = new URL("http://" + LocalZoneUtils.CONFIG_SERVER_HOSTNAME +
                ":" + LocalZoneUtils.CONFIG_SERVER_WEB_SERVICE_PORT + "/state/v1/health");
        assertTrue("Could not start config server", LocalZoneUtils.isReachableURL(configServerUrl, Duration.ofSeconds(120)));

        logger.info("Provisioning nodes");
        Set<String> hostnames = LocalZoneUtils.provisionNodes(HostName.getLocalhost(), numNodesToProvision);
        hostnames.forEach(hostname -> LocalZoneUtils.setState(Node.State.ready, hostname));
    }

    /**
     * Start node-admin in IDE
     * @param pathToContainerStorage Path to where the container data will be stored, the path must exist and must
     *                               be writeable by user, normally /home/docker/container-storage
     */
    void startNodeAdminInIDE(Path pathToContainerStorage) {
        logger.info("Starting node-admin");
        nodeAdminStateUpdater = new ComponentsProviderImpl(docker,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation),
                new StorageMaintainerMock(LocalZoneUtils.getMaintainer(pathToContainerStorage), new CallOrderVerifier()),
                environment).getNodeAdminStateUpdater();
    }

    /**
     * Starts node-admin inside a container
     * @param pathToContainerStorage Path to where the container data will be stored, the path must exist and must
     *                               be writeable by user, normally /home/docker/container-storage
     */
    void startNodeAdminAsContainer(Path pathToContainerStorage) throws UnknownHostException {
        String hostname = InetAddress.getByName("172.18.0.1").getHostName();
        logger.info("Provisioning host at " + hostname);
        LocalZoneUtils.provisionHost(hostname);

        logger.info("Starting node-admin");
        LocalZoneUtils.startNodeAdminIfNeeded(docker, environment, pathToContainerStorage);
        try {
            URL nodeUrl = new URL("http://localhost:" + System.getenv("VESPA_WEB_SERVICE_PORT") + "/");
            assertTrue(LocalZoneUtils.isReachableURL(nodeUrl, Duration.ofSeconds(120)));
            logger.info("Ready");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deploys an app and waits for the node to come up
     * @param pathToAppToDeploy Path to .zip file of the application to deploy
     */
    void deployApplication(Path pathToAppToDeploy) {
        logger.info("Deploying application");
        LocalZoneUtils.deployApp(docker, pathToAppToDeploy);

        // TODO: Automatically find correct node to send request to
        try {
            URL nodeUrl = new URL("http://cnode-1:" + System.getenv("VESPA_WEB_SERVICE_PORT") + "/");
            assertTrue(LocalZoneUtils.isReachableURL(nodeUrl, Duration.ofSeconds(120)));
            logger.info("Ready");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    void deleteApplication() {
        LocalZoneUtils.deleteApplication();
    }
}
