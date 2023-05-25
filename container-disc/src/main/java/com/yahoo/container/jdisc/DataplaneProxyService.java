// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.component.AbstractComponent;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Configures a data plane proxy. Currently using Nginx.
 *
 * @author mortent
 */
public class DataplaneProxyService extends AbstractComponent {

    private static final String PREFIX = "/opt/vespa";
    private static final Path CONFIG_TEMPLATE = Paths.get(PREFIX, "conf/nginx/nginx.conf.template");

    private static final Path clientCertificateFile = Paths.get(PREFIX, "conf/nginx/client_cert.pem");
    private static final Path clientKeyFile = Paths.get(PREFIX, "conf/nginx/client_key.pem");
    private static final Path serverCertificateFile = Paths.get(PREFIX, "conf/nginx/server_cert.pem");
    private static final Path serverKeyFile = Paths.get(PREFIX, "conf/nginx/server_key.pem");

    private static final Path nginxConf = Paths.get(PREFIX, "conf/nginx/nginx.conf");

    private boolean started;

    @Inject
    public DataplaneProxyService() {
        this.started = false;
    }

    public void reconfigure(DataplaneProxyConfig config) {
        try {
            String serverCert = config.serverCertificate();
            String serverKey = config.serverKey();
            String clientCert = config.clientCertificate();
            String clientKey = config.clientKey();

            boolean configChanged = false;
            configChanged |= writeFile(clientCertificateFile, clientCert);
            configChanged |= writeFile(clientKeyFile, clientKey);
            configChanged |= writeFile(serverCertificateFile, serverCert);
            configChanged |= writeFile(serverKeyFile, serverKey);
            configChanged |= writeFile(nginxConf,
                      nginxConfig(
                              clientCertificateFile,
                              clientKeyFile,
                              serverCertificateFile,
                              serverKeyFile,
                              URI.create(config.mTlsEndpoint()),
                              URI.create(config.tokenEndpoint()),
                              config.port(),
                              PREFIX
                      ));
            if (!started) {
                startNginx();
                started = true;
            } else if (configChanged){
                reloadNginx();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reconfiguring data plane proxy", e);
        }
    }

    private void startNginx() {
        try {
            Process startCommand = new ProcessBuilder().command(
                    "nginx",
                    "-c", nginxConf.toString()
            ).start();
            int exitCode = startCommand.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Non-zero exitcode from nginx: %d".formatted(exitCode));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not start nginx", e);
        }
    }

    private void reloadNginx() {
        try {
            Process reloadCommand = new ProcessBuilder().command(
                    "nginx",
                    "-s", "reload"
            ).start();
            int exitCode = reloadCommand.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Non-zero exitcode from nginx: %d".formatted(exitCode));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not start nginx", e);
        }
    }

    private void stopNginx() {
        try {
            Process stopCommand = new ProcessBuilder().command(
                    "nginx",
                    "-s", "reload"
            ).start();
            int exitCode = stopCommand.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Non-zero exitcode from nginx: %d".formatted(exitCode));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not start nginx", e);
        }
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        stopNginx();
    }

    /*
     * Writes a file to disk
     * return true if file was changed, false if no changes
     */
    private boolean writeFile(Path file, String contents) throws IOException {
        Path tempPath = Paths.get(file.toFile().getAbsolutePath() + ".new");
        Files.createDirectories(tempPath.getParent());
        Files.writeString(tempPath, contents);

        if (!Files.exists(file) || Files.mismatch(tempPath, file) > 0) {
            Files.move(tempPath, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } else {
            Files.delete(tempPath);
            return false;
        }
    }

    static String nginxConfig(
            Path clientCert,
            Path clientKey,
            Path serverCert,
            Path serverKey,
            URI mTlsEndpoint,
            URI tokenEndpoint,
            int vespaPort,
            String prefix) {

        try {
            String nginxTemplate = Files.readString(CONFIG_TEMPLATE);
            nginxTemplate = replace(nginxTemplate, "client_cert", clientCert.toString());
            nginxTemplate = replace(nginxTemplate, "client_key", clientKey.toString());
            nginxTemplate = replace(nginxTemplate, "server_cert", serverCert.toString());
            nginxTemplate = replace(nginxTemplate, "server_key", serverKey.toString());
            nginxTemplate = replace(nginxTemplate, "mtls_endpoint", mTlsEndpoint.getHost());
            nginxTemplate = replace(nginxTemplate, "token_endpoint", tokenEndpoint.getHost());
            nginxTemplate = replace(nginxTemplate, "vespa_port", Integer.toString(vespaPort));
            nginxTemplate = replace(nginxTemplate, "prefix", prefix);

            // TODO: verify that all template vars have been expanded
            return nginxTemplate;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create data plane proxy configuration", e);
        }
    }

    private static String replace(String template, String key, String value) {
        return template.replaceAll("\\$\\{%s\\}".formatted(key), value);
    }
}
