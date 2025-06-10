// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.cloud.Environment;
import ai.vespa.cloud.SystemInfo;
import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.http.server.jetty.DataplaneProxyCredentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Configures a data plane proxy. Currently using Nginx.
 *
 * @author mortent
 */
public final class DataplaneProxyService extends AbstractComponent {

    private static final Logger logger = Logger.getLogger(DataplaneProxyService.class.getName());
    private static final String PREFIX = "/opt/vespa";
    private static final String CLOUD_AZURE = "azure";

    private final boolean useAzureProxy;

    private final Path configTemplate;
    private final Path serverCertificateFile;
    private final Path serverKeyFile;
    private final Path nginxConf;

    private final ProxyCommands proxyCommands;
    private final ScheduledThreadPoolExecutor executorService;
    private final Path root;
    private final boolean isDevEnvironment;

    enum NginxState {INITIALIZING, RUNNING, RELOAD_REQUIRED, STOPPED};
    private volatile NginxState state;
    private volatile NginxState wantedState;

    private DataplaneProxyConfig cfg;
    private Path proxyCredentialsCert;
    private Path proxyCredentialsKey;

    @Inject
    public DataplaneProxyService(SystemInfo systemInfo) {
        this(Paths.get(PREFIX), new NginxProxyCommands(), 1, CLOUD_AZURE.equalsIgnoreCase(systemInfo.cloud().name()), systemInfo.zone().environment().equals(Environment.dev));
    }

    public DataplaneProxyService() {
        this(Paths.get(PREFIX), new NginxProxyCommands(), 1, false, false);
    }
    
    DataplaneProxyService(Path root, ProxyCommands proxyCommands, int reloadPeriodMinutes) {
        this(root, proxyCommands, reloadPeriodMinutes, false, false);
    }

    private DataplaneProxyService(Path root, ProxyCommands proxyCommands, int reloadPeriodMinutes, boolean useAzureProxy, boolean isDevEnvironment) {
        this.root = root;
        this.proxyCommands = proxyCommands;
        this.useAzureProxy = useAzureProxy;
        this.isDevEnvironment = isDevEnvironment;
        changeState(NginxState.INITIALIZING);
        wantedState = NginxState.RUNNING;
        configTemplate = root.resolve("conf/nginx/nginx.conf.template");
        serverCertificateFile = root.resolve("conf/nginx/server_cert.pem");
        serverKeyFile = root.resolve("conf/nginx/server_key.pem");
        nginxConf = root.resolve("conf/nginx/nginx.conf");

        executorService = new ScheduledThreadPoolExecutor(1);
        executorService.scheduleAtFixedRate(this::converge, reloadPeriodMinutes, reloadPeriodMinutes, TimeUnit.MINUTES);
    }

    public void reconfigure(DataplaneProxyConfig config, DataplaneProxyCredentials credentialsProvider) {
        synchronized (this) {
            this.cfg = config;
            this.proxyCredentialsCert = credentialsProvider.certificateFile();
            this.proxyCredentialsKey = credentialsProvider.keyFile();
        }
    }

    private void changeState(NginxState newState) {
        state = newState;
    }

    void converge() {
        DataplaneProxyConfig config;
        Path proxyCredentialsCert;
        Path proxyCredentialsKey;
        synchronized (this) {
            config = cfg;
            proxyCredentialsCert = this.proxyCredentialsCert;
            proxyCredentialsKey = this.proxyCredentialsKey;
            this.cfg = null;
            this.proxyCredentialsCert = null;
            this.proxyCredentialsKey = null;
        }
        if (config != null) {
            try {

                String serverCert = config.serverCertificate();
                String serverKey = config.serverKey();

                boolean configChanged = false;
                configChanged |= writeFile(serverCertificateFile, serverCert);
                configChanged |= writeFile(serverKeyFile, serverKey);
                configChanged |= writeFile(nginxConf,
                                           nginxConfig(
                                                   configTemplate,
                                                   proxyCredentialsCert,
                                                   proxyCredentialsKey,
                                                   serverCertificateFile,
                                                   serverKeyFile,
                                                   config.mtlsPort(),
                                                   config.tokenPort(),
                                                   config.tokenEndpoints(),
                                                   root,
                                                   useAzureProxy,
                                                   isDevEnvironment));
                if (configChanged) {
                    logger.log(Level.INFO, "Configuring data plane proxy service. Token endpoints: [%s]"
                            .formatted(String.join(", ", config.tokenEndpoints())));
                }
                if (configChanged && state == NginxState.RUNNING) {
                    changeState(NginxState.RELOAD_REQUIRED);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error reconfiguring data plane proxy", e);
            }
        }
        NginxState convergeTo = wantedState;
        if (convergeTo == NginxState.RUNNING) {
            boolean nginxRunning = proxyCommands.isRunning();
            if (!nginxRunning) {
                try {
                    proxyCommands.start(nginxConf);
                    changeState(convergeTo);
                } catch (Exception e) {
                    logger.log(Level.INFO, "Failed to start nginx, will retry");
                    logger.log(Level.FINE, "Exception from nginx start", e);
                }
            } else {
                if (state == NginxState.RELOAD_REQUIRED) {
                    try {
                        proxyCommands.reload(nginxConf);
                        changeState(convergeTo);
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Failed to reconfigure nginx, will retry.");
                        logger.log(Level.FINE, "Exception from nginx reload", e);
                    }
                } else if (state != convergeTo) {
                    // Already running, but state not updated
                    changeState(convergeTo);
                }
            }
        } else if (convergeTo == NginxState.STOPPED) {
            if (proxyCommands.isRunning()) {
                try {
                    proxyCommands.stop(nginxConf);
                } catch (Exception e) {
                    logger.log(Level.INFO, "Failed to stop nginx, will retry");
                    logger.log(Level.FINE, "Exception from nginx stop", e);
                }
            }
            if (! proxyCommands.isRunning()) {
                changeState(convergeTo);
                executorService.shutdownNow();
            }
        } else {
            logger.warning("Unknown state " + convergeTo);
        }
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        wantedState = NginxState.STOPPED;
        try {
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Error shutting down proxy reload thread", e);
        }
    }

    /*
     * Writes a file to disk
     * return true if file was changed, false if no changes
     */
    private boolean writeFile(Path file, String contents) throws IOException {
        Path tempPath = file.getParent().resolve(file.getFileName().toString() + ".new");
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
            Path configTemplate,
            Path clientCert,
            Path clientKey,
            Path serverCert,
            Path serverKey,
            int vespaMtlsPort,
            int vespaTokenPort,
            List<String> tokenEndpoints,
            Path root,
            boolean useAzureProxy,
            boolean isDevEnvironment) {

        try {
            String nginxTemplate = Files.readString(configTemplate);

            String proxySuffix = useAzureProxy ? "" : "proxy_protocol";
            nginxTemplate = replace(nginxTemplate, "proxy_protocol_suffix", proxySuffix);

            nginxTemplate = replace(nginxTemplate, "client_cert", clientCert.toString());
            nginxTemplate = replace(nginxTemplate, "client_key", clientKey.toString());
            nginxTemplate = replace(nginxTemplate, "server_cert", serverCert.toString());
            nginxTemplate = replace(nginxTemplate, "server_key", serverKey.toString());
            nginxTemplate = replace(nginxTemplate, "vespa_mtls_port", Integer.toString(vespaMtlsPort));
            nginxTemplate = replace(nginxTemplate, "vespa_token_port", Integer.toString(vespaTokenPort));
            nginxTemplate = replace(nginxTemplate, "prefix", root.toString());
            String tokenmapping = tokenEndpoints.stream()
                    .map("        %s vespatoken;"::formatted)
                    .collect(Collectors.joining("\n"));
            nginxTemplate = replace(nginxTemplate, "vespa_token_endpoints", tokenmapping);

            String corsMap = isDevEnvironment ? """
                    map $http_origin $allow_origin {
                            ~^https://.*\\.vespa-cloud.com$ $http_origin;
                            ~^https?://localhost(:\\d+)?$ $http_origin;
                            default "";
                        }
                    """ : "";

            String corsHandler = isDevEnvironment ? """
                    if ($request_method = OPTIONS) {
                                  add_header Access-Control-Allow-Origin $allow_origin;
                                  add_header Vary Origin;
                                  add_header Access-Control-Allow-Headers "Origin,Content-Type,Accept,Authorization";
                                  add_header Access-Control-Allow-Methods "OPTIONS,GET,PUT,DELETE,POST,PATCH";
                                  return 204;
                                }
                                add_header Access-Control-Allow-Origin $allow_origin;
                                add_header Vary Origin;
                    """ : "";

            nginxTemplate = replace(nginxTemplate, "cors_map", corsMap);
            nginxTemplate = replace(nginxTemplate, "cors_handler", corsHandler);

            // TODO: verify that all template vars have been expanded
            return nginxTemplate;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create data plane proxy configuration", e);
        }
    }

    private static String replace(String template, String key, String value) {
        // we want a plain literal replace of "${key}" → value, even if value contains '$'
        return template.replace("${" + key + "}", value);
    }

    // Used for testing
    NginxState state() {
        return state;
    }

    // Used for testing
    NginxState wantedState() {
        return wantedState;
    }

    public interface ProxyCommands {
        void start(Path configFile);
        void stop(Path configFile);
        void reload(Path configFile);
        boolean isRunning();
    }

    public static class NginxProxyCommands implements ProxyCommands {

        @Override
        public void start(Path configFile) {
            try {
                Process startCommand = new ProcessBuilder().command(
                        "nginx",
                        "-c", configFile.toString()
                ).start();
                int exitCode = startCommand.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("Non-zero exitcode from nginx: %d".formatted(exitCode));
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Could not start nginx", e);
            }
        }

        @Override
        public void stop(Path configFile) {
            try {
                Process stopCommand = new ProcessBuilder().command(
                        "nginx",
                        "-s", "stop",
                        "-c", configFile.toString()
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
        public void reload(Path configFile) {
            try {
                Process reloadCommand = new ProcessBuilder().command(
                        "nginx",
                        "-s", "reload",
                        "-c", configFile.toString()
                ).start();
                int exitCode = reloadCommand.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("Non-zero exitcode from nginx: %d".formatted(exitCode));
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Could not start nginx", e);
            }
        }

        @Override
        public boolean isRunning() {
            return ProcessHandle.allProcesses()
                    .map(ProcessHandle::info)
                    .anyMatch(info -> info.command().orElse("").endsWith("nginx"));
        }
    }
}
