// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
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
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Before;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Requires docker daemon, see {@link com.yahoo.vespa.hosted.dockerapi.DockerTestUtils} for more details.
 *
 * To get started:
 *  1. Add config-server and container nodes hostnames to /etc/hosts:
 *      $ sudo ./vespa/node-admin/scripts/etc-hosts.sh
 *  2. Set environmental variables in shell or e.g. ~/.bashrc:
 *      VESPA_HOME="/home/y"
 *      VESPA_WEB_SERVICE_PORT="4080"
 *
 * Linux only:
 *  1. Create /home/docker/container-storage with read/write permissions
 *
 *
 * Issues:
 *  1. If you cannot make Docker Toolbox start, try starting Virtualbox and turn off the "default" machine
 *  2. If the above is not enough try "sudo ifconfig vboxnet0 down && sudo ifconfig vboxnet0 up" (see https://github.com/docker/kitematic/issues/1193)
 *
 * @author freva
 */
public class RunVespaLocal {
    private static final Environment environment = new Environment(
            Collections.singleton(LocalZoneUtils.CONFIG_SERVER_HOSTNAME), "prod", "vespa-local",
            HostName.getLocalhost(), new InetAddressResolver());
    private static final Maintainer maintainer = mock(Maintainer.class);

    private final Docker docker;
    private final DockerImage vespaBaseImage;
    private final Path pathToAppToDeploy;

    private final Logger logger = Logger.getLogger("RunVespaLocal");


    RunVespaLocal(DockerImage vespaBaseImage, Path pathToAppToDeploy) {
        this.docker = DockerTestUtils.getDocker();
        this.vespaBaseImage = vespaBaseImage;
        this.pathToAppToDeploy = pathToAppToDeploy;
    }

    void runVespaLocalTest() throws IOException, InterruptedException, ExecutionException {
        DockerTestUtils.OS operatingSystem = DockerTestUtils.getSystemOS();
        if (operatingSystem == DockerTestUtils.OS.Mac_OS_X) {
            when(maintainer.pathInHostFromPathInNode(any(), any())).thenReturn(Paths.get("/tmp/"));
        } else {
            when(maintainer.pathInHostFromPathInNode(any(), any())).thenCallRealMethod();
        }
        when(maintainer.pathInNodeAdminToNodeCleanup(any())).thenReturn(Paths.get("/tmp"));
        when(maintainer.pathInNodeAdminFromPathInNode(any(), any())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            return maintainer.pathInHostFromPathInNode((ContainerName) args[0], (String) args[1]);
        });

        if (!docker.imageIsDownloaded(vespaBaseImage)) {
            logger.info("Pulling " + vespaBaseImage.asString() + " (This may take a while)");
            docker.pullImageAsync(vespaBaseImage).get();
        }

        logger.info("Building " + LocalZoneUtils.VESPA_LOCAL_IMAGE.asString());
        LocalZoneUtils.buildVespaLocalDockerImage(docker, vespaBaseImage);

        logger.info("Starting config-server");
        assertTrue("Could not start config server", LocalZoneUtils.startConfigServerIfNeeded(docker, environment));

        logger.info("Provisioning nodes");
        try {
            Set<String> hostnames = LocalZoneUtils.provisionNodes(HostName.getLocalhost(), 5);
            for (String hostname : hostnames) {
                try {
                    LocalZoneUtils.setState(Node.State.ready, hostname);
                } catch (RuntimeException e) {
                    logger.warning(e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            logger.warning(e.getMessage());
        }

        logger.info("Deploying application");
        LocalZoneUtils.deployApp(docker, pathToAppToDeploy);

        logger.info("Starting node-admin");
        NodeAdminStateUpdater nodeAdminStateUpdater = new ComponentsProviderImpl(docker,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation),
                new StorageMaintainerMock(maintainer, new CallOrderVerifier()),
                environment).getNodeAdminStateUpdater();

        logger.info("Ready");
        // TODO: Automatically find correct node to send request to
        URL url = new URL("http://cnode-1:" + System.getenv("VESPA_WEB_SERVICE_PORT") + "/");
        Instant start = Instant.now();
        boolean okResponse = false;
        do {
            try {
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                if (http != null && http.getResponseCode() == 200) okResponse = true;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        } while (! okResponse && Instant.now().isBefore(start.plusSeconds(120)));
        assertTrue(okResponse);

        LocalZoneUtils.deleteApplication();
        nodeAdminStateUpdater.deconstruct();
    }
}
