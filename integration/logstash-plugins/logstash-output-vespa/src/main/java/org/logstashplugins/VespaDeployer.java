// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package org.logstashplugins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VespaDeployer {
    private final QuickStartConfig config;
    private static final Logger logger = LogManager.getLogger(VespaDeployer.class);
    private static final AtomicBoolean deploymentInstructionsShown = new AtomicBoolean(false);

    public VespaDeployer(QuickStartConfig config) {
        this.config = config;
    }

    void deployApplicationPackage() {
        // we can only deploy one application package at a time, so we create a lock file
        Path writeLockFile = Paths.get(config.getApplicationPackageDir(), "write.lock");
        try {
            Files.createFile(writeLockFile);
        } catch (FileAlreadyExistsException e) {
            logger.warn("Write lock file exists. We'll assume another thread is already deploying the application package or we're still writing the package.");
            return;
        } catch (IOException e) {
            logger.error("Error creating write lock file: {}", e.getMessage());
            return;
        }

        try {
            // Check if we're in Vespa Cloud mode
            if (config.isVespaCloud()) {
                showVespaCloudDeploymentInstructions();
                return;
            }
            
            // Continue with local Vespa deployment
            byte[] zipContent = createApplicationZip();
            
            HttpURLConnection connection = null;
            try {
                URI uri = config.getConfigServer().resolve("/application/v2/tenant/default/prepareandactivate");
                URL url = uri.toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/zip");
                connection.setDoOutput(true);
                
                connection.getOutputStream().write(zipContent);
                
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    String error = new String(connection.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    throw new IOException("Failed to deploy application package. Response code: " + 
                        responseCode + ", error: " + error);
                }
                logger.info("Successfully deployed application package to local Vespa");
            } catch (java.net.ConnectException e) {
                logger.error("Connection refused when trying to deploy to Vespa at {}.", config.getConfigServer());
                logger.error("Make sure the config server is running at the specified URL.");
                logger.error("Error: {}", e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        } catch (IOException e) {
            logger.error("Error deploying application package: {}", e.getMessage());
        } finally {
            try {
                Files.delete(writeLockFile);
            } catch (IOException e) {
                logger.error("Error deleting lock file: {}", e.getMessage());
            }
        }
    }
    
    private void showVespaCloudDeploymentInstructions() {
        // Only show instructions once
        if (deploymentInstructionsShown.compareAndSet(false, true)) {
            String tenant = config.getVespaCloudTenant();
            String application = config.getVespaCloudApplication();
            String instance = config.getVespaCloudInstance();
            String appDir = config.getApplicationPackageDir();
            
            logger.info("===============================================================");
            logger.info("Application package for Vespa Cloud has been generated at: {}", appDir);
            logger.info("To deploy to Vespa Cloud, use the Vespa CLI with the following steps:");
            logger.info("");
            logger.info("1. If you haven't already, install the Vespa CLI: https://docs.vespa.ai/en/vespa-cli.html");
            logger.info("");
            logger.info("2. Point the CLI to your Vespa Cloud application:");
            logger.info("   vespa config set target cloud");
            logger.info("   vespa config set application {}.{}.{}", tenant, application, instance);
            logger.info("");
            logger.info("3. Authenticate with Vespa Cloud:");
            logger.info("   vespa auth login");
            logger.info("");
            logger.info("4. Deploy your application:");
            logger.info("   cd {}", appDir);
            logger.info("   vespa deploy --wait 900");
            logger.info("");
            logger.info("For more information, see: https://cloud.vespa.ai/en/getting-started");
            logger.info("===============================================================");
        }
    }

    byte[] createApplicationZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add services.xml
            addFileToZip(zos, "services.xml");
            
            // Add schema file
            String schemaFileName = "schemas/" + config.getDocumentType() + ".sd";
            addFileToZip(zos, schemaFileName);
        }
        return baos.toByteArray();
    }

    private void addFileToZip(ZipOutputStream zos, String fileName) throws IOException {
        Path filePath = Paths.get(config.getApplicationPackageDir(), fileName);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }
        
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        Files.copy(filePath, zos);
        zos.closeEntry();
    }
}
