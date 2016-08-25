package com.yahoo.vespa.hosted.docker.api.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;

import com.yahoo.component.AbstractComponent;
import com.yahoo.nodeadmin.dockerapi.DockerConfig;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * A class wrapping the DockerJava library for OSGI to avoid dependency problem between this library and Vespa.
 * @author dybdahl
 */
public class DockerApi  extends AbstractComponent {
    private static final int DOCKER_MAX_PER_ROUTE_CONNECTIONS = 10;
    private static final int DOCKER_MAX_TOTAL_CONNECTIONS = 100;
    private static final int DOCKER_CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(100);
    private static final int DOCKER_READ_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(30);

    private final DockerClient dockerClient;

    public DockerApi(DockerConfig config) {
        DefaultDockerClientConfig dockerClientConfig = buildDockerClientConfig(config);

        dockerClient = DockerClientImpl.getInstance(dockerClientConfig)
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

    static DefaultDockerClientConfig buildDockerClientConfig(DockerConfig config) {
        DefaultDockerClientConfig.Builder dockerConfigBuilder = new DefaultDockerClientConfig.Builder()
                .withDockerHost(config.uri());

        if (URI.create(config.uri()).getScheme().equals("tcp") && !config.caCertPath().isEmpty()) {
            // In current version of docker-java (3.0.2), withDockerTlsVerify() only effect is when using it together
            // with withDockerCertPath(), where setting withDockerTlsVerify() must be set to true, otherwise the
            // cert path parameter will be ignored.
            // withDockerTlsVerify() has no effect when used with withCustomSslConfig()
            dockerConfigBuilder
                    .withCustomSslConfig(new VespaSSLConfig(config));
        }

        return dockerConfigBuilder.build();
    }
}
