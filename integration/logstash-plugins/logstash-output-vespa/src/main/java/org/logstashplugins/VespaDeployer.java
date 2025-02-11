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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VespaDeployer {
    private final DryRunConfig config;
    private static final Logger logger = LogManager.getLogger(VespaDeployer.class);

    public VespaDeployer(DryRunConfig config) {
        this.config = config;
    }

    void deployApplicationPackage() {
        // we can only deploy one application package at a time, so we create a lock file
        Path writeLockFile = Paths.get(config.getApplicationPackageDir(), "write.lock");
        try {
            Files.createFile(writeLockFile);
        } catch (FileAlreadyExistsException e) {
            logger.error("Write lock file exists. Either the application is already being deployed or something went wrong.");
            return;
        } catch (IOException e) {
            logger.error("Error creating write lock file: {}", e.getMessage());
            return;
        }

        HttpURLConnection connection = null;
        try {
            byte[] zipContent = createApplicationZip();
            
            URI uri = URI.create(config.getConfigServer() + "/application/v2/tenant/default/prepareandactivate");
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
            logger.info("Successfully deployed application package");
        
        } catch (IOException e) {
            logger.error("Error deploying application package: {}", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            try {
                Files.delete(writeLockFile);
            } catch (IOException e) {
                logger.error("Error deleting lock file: {}", e.getMessage());
            }
        }
    }

    private byte[] createApplicationZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add services.xml
            addFileToZip(zos, "services.xml");
            
            // Add schema file
            // TODO: handle multiple schemas
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
