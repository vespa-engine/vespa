package com.yahoo.vespa.hosted.docker.api.docker;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;

import com.github.dockerjava.api.DockerClient;
import com.yahoo.component.AbstractComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A class wrapping the DockerJava library for OSGI to avoid dependency problem between this library and Vespa.
 * @author dybdahl
 */
public class DockerApi  extends AbstractComponent {
    private static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";
    private static final String LABEL_VALUE_MANAGEDBY = "node-admin";
    private static final Map<String, String> CONTAINER_LABELS = new HashMap<>();

    private static final int DOCKER_MAX_PER_ROUTE_CONNECTIONS = 10;
    private static final int DOCKER_MAX_TOTAL_CONNECTIONS = 100;
    private static final int DOCKER_CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(100);
    private static final int DOCKER_READ_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(30);

    static {
        CONTAINER_LABELS.put(LABEL_NAME_MANAGEDBY, LABEL_VALUE_MANAGEDBY);
    }

    private final DockerClient dockerClient;

    public DockerApi() {
        dockerClient = DockerClientImpl.getInstance(new DefaultDockerClientConfig.Builder()
                // Talks HTTP(S) over a TCP port. The docker client library does only support tcp:// and unix://
                .withDockerHost("unix:///host/var/run/docker.sock") // Alternatively, but
                // does not work due to certificate issues as if Aug 18th 2016: config.uri().replace("https", "tcp"))
                .withDockerTlsVerify(false)
                //.withCustomSslConfig(new VespaSSLConfig(config))
                // We can specify which version of the docker remote API to use, otherwise, use latest
                // e.g. .withApiVersion("1.23")
                .build())
                .withDockerCmdExecFactory(
                        new JerseyDockerCmdExecFactory()
                                .withMaxPerRouteConnections(DOCKER_MAX_PER_ROUTE_CONNECTIONS)
                                .withMaxTotalConnections(DOCKER_MAX_TOTAL_CONNECTIONS)
                                .withConnectTimeout(DOCKER_CONNECT_TIMEOUT_MILLIS)
                                .withReadTimeout(DOCKER_READ_TIMEOUT_MILLIS)
                );
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }
}
