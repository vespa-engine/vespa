package com.yahoo.vespa.hosted.docker.api.docker;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.yahoo.nodeadmin.dockerapi.DockerConfig;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import static org.junit.Assert.assertTrue;

/**
 * @author valerijf
 */
public class DockerApiTest {

    @Test
    public void testDockerConfigWithUnixPath() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String dockerUri = "unix:///var/run/docker.sock";
        DockerConfig config = createConfig(dockerUri, null, null, null);
        DefaultDockerClientConfig clientConfig = DockerApi.buildDockerClientConfig(config);

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was set when using socket", clientConfig.getSSLConfig() == null);
    }

    @Test
    public void testDockerConfigWithTcpPathWithoutSSL() {
        String dockerUri = "tcp://127.0.0.1:2376";
        DockerConfig config = createConfig(dockerUri, null, null, null);
        DefaultDockerClientConfig clientConfig = DockerApi.buildDockerClientConfig(config);

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was set", clientConfig.getSSLConfig() == null);
    }

    @Test
    public void testDockerConfigWithTcpPathWithSslConfig() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String dockerUri = "tcp://127.0.0.1:2376";
        DockerConfig config = createConfig(dockerUri, "/some/path/ca", "/some/path/cert", "/some/path/key");
        DefaultDockerClientConfig clientConfig = DockerApi.buildDockerClientConfig(config);

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was not set", clientConfig.getSSLConfig() != null);
    }

    @Test(expected=RuntimeException.class)
    public void testDockerConfigWithTcpPathWithInvalidSslConfig() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String dockerUri = "tcp://127.0.0.1:2376";
        DockerConfig config = createConfig(dockerUri, "/some/path/ca", "/some/path/cert", "/some/path/key");
        DefaultDockerClientConfig clientConfig = DockerApi.buildDockerClientConfig(config);

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was not set", clientConfig.getSSLConfig() != null);

        // SSL certificates are read during the getSSLContext(), the invalid paths should cause a RuntimeException
        clientConfig.getSSLConfig().getSSLContext();
    }


    private static DockerConfig createConfig(String uri, String caCertPath, String clientCertPath, String clientKeyPath) {
        DockerConfig.Builder configBuilder = new DockerConfig.Builder();

        if (uri != null)            configBuilder.uri(uri);
        if (caCertPath != null)     configBuilder.caCertPath(caCertPath);
        if (clientCertPath != null) configBuilder.clientCertPath(clientCertPath);
        if (clientKeyPath != null)  configBuilder.clientKeyPath(clientKeyPath);

        return new DockerConfig(configBuilder);
    }
}
