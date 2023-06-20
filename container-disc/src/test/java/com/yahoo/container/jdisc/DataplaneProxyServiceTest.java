// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.common.jimfs.Jimfs;
import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.jdisc.http.server.jetty.DataplaneProxyCredentials;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.X509CertificateWithKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataplaneProxyServiceTest {
    private FileSystem fileSystem = Jimfs.newFileSystem();
    DataplaneProxyService.ProxyCommands proxyCommandsMock = Mockito.mock(DataplaneProxyService.ProxyCommands.class);

    @Test
    public void starts_and_reloads_if_no_errors() throws IOException {
        DataplaneProxyService service = dataplaneProxyService(proxyCommandsMock);

        assertEquals(DataplaneProxyService.NginxState.INITIALIZING, service.state());
        service.reconfigure(proxyConfig(), credentials(fileSystem));

        // Simulate executor next tick
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());

        // Trigger reload by recreating the proxy config (generates new server cert)
        service.reconfigure(proxyConfig(), credentials(fileSystem));
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());
    }

    @Test
    public void retries_startup_errors() throws IOException {
        Mockito.doThrow(new RuntimeException("IO error")).doNothing().when(proxyCommandsMock).start(any());
        DataplaneProxyService service = dataplaneProxyService(proxyCommandsMock);

        assertEquals(DataplaneProxyService.NginxState.INITIALIZING, service.state());
        service.reconfigure(proxyConfig(), credentials(fileSystem));

        // Start nginx, starting will fail, so the service should be in INITIALIZING state
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.INITIALIZING, service.state());
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());
    }

    @Test
    public void retries_reload_errors() throws IOException {
        Mockito.doThrow(new RuntimeException("IO error")).doNothing().when(proxyCommandsMock).reload();
        when(proxyCommandsMock.isRunning()).thenReturn(false);
        DataplaneProxyService service = dataplaneProxyService(proxyCommandsMock);

        // Make sure service in running state
        service.reconfigure(proxyConfig(), credentials(fileSystem));
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());
        when(proxyCommandsMock.isRunning()).thenReturn(true);

        // Trigger reload, verifies 2nd attempt succeeds
        service.reconfigure(proxyConfig(), credentials(fileSystem));
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.RELOAD_REQUIRED, service.state());
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());
        verify(proxyCommandsMock, times(2)).reload();
    }

    @Test
    public void converges_to_wanted_state_when_nginx_not_running() throws IOException {
        DataplaneProxyService.ProxyCommands proxyCommands = new TestProxyCommands();
        DataplaneProxyService service = dataplaneProxyService(proxyCommands);

        assertFalse(proxyCommands.isRunning());
        service.reconfigure(proxyConfig(), credentials(fileSystem));
        service.converge();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());
        assertTrue(proxyCommands.isRunning());

        // Simulate nginx process dying
        proxyCommands.stop();
        assertFalse(proxyCommands.isRunning());
        service.converge();
        assertTrue(proxyCommands.isRunning());
    }

    @Test
    public void shuts_down() throws IOException {
        DataplaneProxyService.ProxyCommands proxyCommands = new TestProxyCommands();
        DataplaneProxyService service = dataplaneProxyService(proxyCommands);
        service.converge();
        assertTrue(proxyCommands.isRunning());
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());

        new Thread(service::deconstruct).start(); // deconstruct will block until nginx is stopped
        // Wait for above thread to set the wanted state to STOPPED
        while (service.wantedState() != DataplaneProxyService.NginxState.STOPPED) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        service.converge();
        assertEquals(service.state(), DataplaneProxyService.NginxState.STOPPED);
        assertFalse(proxyCommands.isRunning());
    }

    private DataplaneProxyService dataplaneProxyService(DataplaneProxyService.ProxyCommands proxyCommands) throws IOException {
        Path root = fileSystem.getPath("/opt/vespa");

        Path nginxConf = root.resolve("conf/nginx/nginx.conf.template");
        Files.createDirectories(nginxConf.getParent());
        Files.write(nginxConf, "".getBytes(StandardCharsets.UTF_8));

        DataplaneProxyService service = new DataplaneProxyService(root, proxyCommands, 100);
        return service;
    }

    private DataplaneProxyConfig proxyConfig() {
        X509CertificateWithKey selfSigned = X509CertificateUtils.createSelfSigned("cn=test", Duration.ofMinutes(10));
        return new DataplaneProxyConfig.Builder()
                .port(1234)
                .serverCertificate(X509CertificateUtils.toPem(selfSigned.certificate()))
                .serverKey(KeyUtils.toPem(selfSigned.privateKey()))
                .build();
    }

    private DataplaneProxyCredentials credentials(FileSystem fileSystem) {
        Path path = fileSystem.getPath("/tmp");
        uncheck(() -> Files.createDirectories(path));
        return new DataplaneProxyCredentials(path.resolve("cert.pem"), path.resolve("key.pem"));
    }

    private static class TestProxyCommands implements DataplaneProxyService.ProxyCommands {
        private boolean running = false;

        @Override
        public void start(Path configFile) {
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public void reload() {

        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
