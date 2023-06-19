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
import static org.mockito.ArgumentMatchers.any;

public class DataplaneProxyServiceTest {
    private FileSystem fileSystem = Jimfs.newFileSystem();
    DataplaneProxyService.ProxyCommands proxyCommands = Mockito.mock(DataplaneProxyService.ProxyCommands.class);

    @Test
    public void starts_and_reloads_if_no_errors() throws IOException {
        DataplaneProxyService service = dataplaneProxyService(proxyCommands);

        assertEquals(DataplaneProxyService.NginxState.INITIALIZING, service.state());
        service.reconfigure(proxyConfig(), credentials(fileSystem));

        // Simulate executor next tick
        service.startOrReloadNginx();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());

        // Trigger reload by recreating the proxy config (generates new server cert)
        service.reconfigure(proxyConfig(), credentials(fileSystem));
        service.startOrReloadNginx();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());
    }

    @Test
    public void retries_startup_errors() throws IOException {
        Mockito.doThrow(new RuntimeException("IO error")).doNothing().when(proxyCommands).start(any());
        DataplaneProxyService service = dataplaneProxyService(proxyCommands);

        assertEquals(DataplaneProxyService.NginxState.INITIALIZING, service.state());
        service.reconfigure(proxyConfig(), credentials(fileSystem));

        // Start nginx,
        service.startOrReloadNginx();
        assertEquals(DataplaneProxyService.NginxState.INITIALIZING, service.state());
        service.startOrReloadNginx();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());
    }

    @Test
    public void retries_reload_errors() throws IOException {
        Mockito.doThrow(new RuntimeException("IO error")).doNothing().when(proxyCommands).reload();
        DataplaneProxyService service = dataplaneProxyService(proxyCommands);

        // Make sure service in running state
        service.reconfigure(proxyConfig(), credentials(fileSystem));
        service.startOrReloadNginx();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());

        // Trigger reload, verifies 2nd attempt succeeds
        service.reconfigure(proxyConfig(), credentials(fileSystem));
        service.startOrReloadNginx();
        assertEquals(DataplaneProxyService.NginxState.RELOAD_REQUIRED, service.state());
        service.startOrReloadNginx();
        assertEquals(DataplaneProxyService.NginxState.RUNNING, service.state());

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
}
