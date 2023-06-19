// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.server.jetty.DataplaneProxyCredentials;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configures a data plane proxy. Currently using Nginx.
 *
 * @author mortent
 */
public class DataplaneProxyService extends AbstractComponent {

    private static Logger logger = Logger.getLogger(DataplaneProxyService.class.getName());
    private static final String PREFIX = "/opt/vespa";

    private final Path configTemplate;
    private final Path serverCertificateFile;
    private final Path serverKeyFile;
    private final Path nginxConf;

    private final ProxyCommands proxyCommands;
    private final ScheduledThreadPoolExecutor executorService;
    private final Path root;

    enum NginxState {INITIALIZING, STARTING, RUNNING, RELOAD_REQUIRED, CONFIG_CHANGE_IN_PROGRESS, STOPPED};
    private NginxState state;


    @Inject
    public DataplaneProxyService() {
        this(Paths.get(PREFIX), new NginxProxyCommands(), 1);
    }
    
    DataplaneProxyService(Path root, ProxyCommands proxyCommands, int reloadPeriodMinutes) {
        this.root = root;
        this.proxyCommands = proxyCommands;
        changeState(NginxState.INITIALIZING);
        configTemplate = root.resolve("conf/nginx/nginx.conf.template");
        serverCertificateFile = root.resolve("conf/nginx/server_cert.pem");
        serverKeyFile = root.resolve("conf/nginx/server_key.pem");
        nginxConf = root.resolve("conf/nginx/nginx.conf");

        executorService = new ScheduledThreadPoolExecutor(1);
        executorService.scheduleAtFixedRate(this::startOrReloadNginx, reloadPeriodMinutes, reloadPeriodMinutes, TimeUnit.MINUTES);

    }

    public void reconfigure(DataplaneProxyConfig config, DataplaneProxyCredentials credentialsProvider) {
        NginxState prevState = state;
        changeState(NginxState.CONFIG_CHANGE_IN_PROGRESS);
        try {

            String serverCert = config.serverCertificate();
            String serverKey = config.serverKey();

            boolean configChanged = false;
            configChanged |= writeFile(serverCertificateFile, serverCert);
            configChanged |= writeFile(serverKeyFile, serverKey);
            configChanged |= writeFile(nginxConf,
                      nginxConfig(
                              configTemplate,
                              credentialsProvider.certificateFile(),
                              credentialsProvider.keyFile(),
                              serverCertificateFile,
                              serverKeyFile,
                              config.port(),
                              root
                      ));
            if (prevState == NginxState.INITIALIZING) {
                changeState(NginxState.STARTING);
            } else if (configChanged && prevState == NginxState.RUNNING) {
                changeState(NginxState.RELOAD_REQUIRED);
            } else {
                changeState(prevState);
            }
        } catch (IOException e) {
            changeState(prevState);
            throw new RuntimeException("Error reconfiguring data plane proxy", e);
        }
    }

    private synchronized void changeState(NginxState newState) {
        state = newState;
    }

    void startOrReloadNginx() {
        if (state == NginxState.CONFIG_CHANGE_IN_PROGRESS) {
            return;
        } else if (state == NginxState.STARTING) {
            try {
                proxyCommands.start(nginxConf);
                changeState(NginxState.RUNNING);
            } catch (Exception e) {
                logger.log(Level.INFO, "Failed to start nginx, will retry");
            }
        } else if (state == NginxState.RELOAD_REQUIRED){
            try {
                proxyCommands.reload();
                changeState(NginxState.RUNNING);
            } catch (Exception e) {
                logger.log(Level.INFO, "Failed to reconfigure nginx, will retry.");
            }
        }
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        try {
            executorService.shutdownNow();
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Error shutting down proxy reload thread");
        }
        try {
            proxyCommands.stop();
            changeState(NginxState.STOPPED);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed shutting down nginx");
        }
    }

    /*
     * Writes a file to disk
     * return true if file was changed, false if no changes
     */
    private boolean writeFile(Path file, String contents) throws IOException {
        Path tempPath = file.getParent().resolve(file.getFileName().toString() + ".new");
//        Path tempPath = Paths.get(file.toFile().getAbsolutePath() + ".new");
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
            int vespaPort,
            Path root) {

        try {
            String nginxTemplate = Files.readString(configTemplate);
            nginxTemplate = replace(nginxTemplate, "client_cert", clientCert.toString());
            nginxTemplate = replace(nginxTemplate, "client_key", clientKey.toString());
            nginxTemplate = replace(nginxTemplate, "server_cert", serverCert.toString());
            nginxTemplate = replace(nginxTemplate, "server_key", serverKey.toString());
            nginxTemplate = replace(nginxTemplate, "vespa_port", Integer.toString(vespaPort));
            nginxTemplate = replace(nginxTemplate, "prefix", root.toString());

            // TODO: verify that all template vars have been expanded
            return nginxTemplate;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create data plane proxy configuration", e);
        }
    }

    private static String replace(String template, String key, String value) {
        return template.replaceAll("\\$\\{%s\\}".formatted(key), value);
    }

    NginxState state() {
        return state;
    }

    public interface ProxyCommands {
        void start(Path configFile);
        void stop();
        void reload();
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
        public void stop() {
            try {
                Process stopCommand = new ProcessBuilder().command(
                        "nginx",
                        "-s", "stop"
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
        public void reload() {
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
    }
}
