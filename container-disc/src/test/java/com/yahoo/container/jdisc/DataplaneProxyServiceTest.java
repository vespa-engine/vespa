// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.List;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataplaneProxyServiceTest {
    private FileSystem fileSystem = Jimfs.newFileSystem();
    DataplaneProxyService.ProxyCommands proxyCommandsMock = mock(DataplaneProxyService.ProxyCommands.class);

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
        Mockito.doThrow(new RuntimeException("IO error")).doNothing().when(proxyCommandsMock).reload(any());
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
        verify(proxyCommandsMock, times(2)).reload(any());
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
        proxyCommands.stop(null);
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

    @Test
    public void stops_executor_when_nginx_stop_throws() throws IOException, InterruptedException {
        DataplaneProxyService.ProxyCommands mockProxyCommands = mock(DataplaneProxyService.ProxyCommands.class);
        DataplaneProxyService service = dataplaneProxyService(mockProxyCommands);
        service.converge();
        when (mockProxyCommands.isRunning()).thenReturn(true);
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());

        reset(proxyCommandsMock);

        when(mockProxyCommands.isRunning()).thenReturn(true).thenReturn(false);
        doThrow(new RuntimeException("Failed to stop proxy")).when(proxyCommandsMock).stop(any());
        Thread thread = new Thread(service::deconstruct);// deconstruct will block until nginx is stopped
        thread.start();

        // Wait for above thread to set the wanted state to STOPPED
        while (service.wantedState() != DataplaneProxyService.NginxState.STOPPED) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        service.converge();
        assertEquals(service.state(), DataplaneProxyService.NginxState.STOPPED);
        thread.join();

        verify(mockProxyCommands, times(1)).stop(any());
    }

    @Test
    public void azureProxyConfigUsesAzureSettings() throws IOException {
        FileSystem fs = Jimfs.newFileSystem();
        Path templatePath = fs.getPath("/opt/vespa/conf/nginx/nginx.conf.template");
        Files.createDirectories(templatePath.getParent());
        String template =
                """
                        remote_expr:${remote_addr_expr}
                        proxy_suffix:${proxy_protocol_suffix}
                        proxy_on:${proxy_protocol_on}
                        proxy_headers:${proxy_set_headers}
                        client_cert:${client_cert}
                        client_key:${client_key}
                        server_cert:${server_cert}
                        server_key:${server_key}
                        mtls_port:${vespa_mtls_port}
                        token_port:${vespa_token_port}
                        prefix:${prefix}
                        tokens:
                        ${vespa_token_endpoints}
                        """;
        Files.writeString(templatePath, template);

        Path clientCert = fs.getPath("/c.pem");
        Path clientKey  = fs.getPath("/k.pem");
        Path serverCert = fs.getPath("/s.pem");
        Path serverKey  = fs.getPath("/t.pem");
        Files.createFile(clientCert);
        Files.createFile(clientKey);
        Files.createFile(serverCert);
        Files.createFile(serverKey);

        String out = DataplaneProxyService.nginxConfig(templatePath, clientCert, clientKey, serverCert, serverKey, 1111, 2222, List.of("one", "two"), fs.getPath("/opt/vespa"), true);

        assertTrue(out.contains("remote_expr:$remote_addr"), "Should use $remote_addr when on Azure");
        assertTrue(out.contains("proxy_suffix:"), "Suffix must be empty on Azure");
        assertTrue(out.contains("proxy_on:"), "proxy_protocol on; must be removed on Azure");
        assertTrue(out.contains("proxy_headers:proxy_set_header X-Forwarded-For $http_x_forwarded_for;"), "Should set Azure-specific X-Forwarded-For header");
        assertTrue(out.contains("one vespatoken;"), "Should map first endpoint");
        assertTrue(out.contains("two vespatoken;"), "Should map second endpoint");
    }

    @Test
    public void defaultProxyConfigUsesProxyProtocol() throws IOException {
        FileSystem fs = Jimfs.newFileSystem();
        Path templatePath = fs.getPath("/opt/vespa/conf/nginx/nginx.conf.template");
        Files.createDirectories(templatePath.getParent());
        String template =
                """
                        remote_expr:${remote_addr_expr}
                        proxy_suffix:${proxy_protocol_suffix}
                        proxy_on:${proxy_protocol_on}
                        proxy_headers:${proxy_set_headers}
                        """;
        Files.writeString(templatePath, template);

        Path dummy = fs.getPath("/dummy.pem");
        Files.createFile(dummy);

        String out = DataplaneProxyService.nginxConfig(templatePath, dummy, dummy, dummy, dummy, 1, 2, List.of("e"), fs.getPath("/pfx"), false);

        assertTrue(out.contains("remote_expr:$proxy_protocol_addr - $remote_addr"), "Should include proxy_protocol_addr when not on Azure");
        assertTrue(out.contains("proxy_suffix:proxy_protocol"), "Should add 'proxy_protocol' suffix when not on Azure");
        assertTrue(out.contains("proxy_on:proxy_protocol on;"), "Should enable proxy_protocol on when not on Azure");
        assertTrue(out.contains("proxy_headers:proxy_set_header X-Forwarded-For $proxy_protocol_addr;"), "Should set proxy_protocol-based X-Forwarded-For header");
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
                .mtlsPort(1234)
                .tokenPort(1235)
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
        public void stop(Path configFile) {
            running = false;
        }

        @Override
        public void reload(Path configFile) {

        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
