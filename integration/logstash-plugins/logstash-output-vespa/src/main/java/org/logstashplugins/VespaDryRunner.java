package org.logstashplugins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import co.elastic.logstash.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.logstash.LockException;
import org.logstash.ObjectMappers;
import java.nio.file.FileAlreadyExistsException;

public class VespaDryRunner {
    private static final Logger logger = LogManager.getLogger(VespaDryRunner.class);
    private final DryRunConfig config;
    private final Map<String, String> defaultTypeMapping;
    private final Map<String, String> detectedFields = new HashMap<>();
    private final ObjectMapper objectMapper;
    private int emptyBatchesCount = 0;

    public VespaDryRunner(DryRunConfig config) {
        this.config = config;
        this.objectMapper = ObjectMappers.JSON_MAPPER;

        // Initialize default type mapping
        this.defaultTypeMapping = new HashMap<>();
        // TODO: add all types here, including tensors and arrays
        // TODO: support multiple mappings per type (e.g. index + attribute)
        defaultTypeMapping.put("string", 
            "type string {\n" +
            "            indexing: summary | index\n" +
            "        }");
        defaultTypeMapping.put("long", 
            "type long {\n" +
            "            indexing: summary | attribute\n" +
            "        }");

        // create the application package directory, if it doesn't exist. Also write the "schemas" directory within it
        Path applicationPackageDir = Paths.get(config.getApplicationPackageDir());
        if (!Files.exists(applicationPackageDir)) {
            try {
                Path schemasDir = Paths.get(config.getApplicationPackageDir(), "schemas");
                Files.createDirectories(schemasDir);
            } catch (IOException e) {
                logger.error("Error creating application package directory: {}", e.getMessage());
                throw new IllegalArgumentException("Error creating application package directory: " + e.getMessage());
            }
        }
        // delete the write.lock file, if it exists
        Path writeLockFile = Paths.get(config.getApplicationPackageDir(), "write.lock");
        if (Files.exists(writeLockFile)) {
            try {
                Files.delete(writeLockFile);
            } catch (IOException e) {
                logger.error("Error deleting write.lock file: {}", e.getMessage());
            }
        }
        // copy the .vespaignore file to the application package directory
        Path vespaIgnoreFile = Paths.get(config.getApplicationPackageDir(), ".vespaignore");
        if (Files.exists(vespaIgnoreFile)) {
            try {
                Files.copy(vespaIgnoreFile, Paths.get(config.getApplicationPackageDir(), ".vespaignore"));
            } catch (IOException e) {
                logger.error("Error copying .vespaignore file: {}", e.getMessage());
            }
        }
    }

    public void run(Collection<Event> events) {
        // the batch may be empty - a hiccup or we're done processing?
        if (events.isEmpty()) {
            emptyBatchesCount++;
            if (config.isDeployPackage() && emptyBatchesCount >= config.getIdleBatches()) {
                deployApplicationPackage();
                emptyBatchesCount = 0;
            }
            return;
        }
        // Got events - reset counter
        emptyBatchesCount = 0;

        // Process each event to detect field types
        for (Event event : events) {
            try {
                // Convert to JSON and back to get clean Java types
                String eventJson = objectMapper.writeValueAsString(event.getData());
                Map<String, Object> cleanData = objectMapper.readValue(eventJson, 
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                
                logger.debug("Processing event: {}", cleanData);
                for (Map.Entry<String, Object> entry : cleanData.entrySet()) {
                    String fieldName = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (value == null) {
                        logger.debug("Field {} has null value, skipping", fieldName);
                        continue;
                    }
                    
                    String type = detectType(value);
                    if (type != null) {
                        String existingType = detectedFields.get(fieldName);
                        if (existingType != null && !existingType.equals(type)) {
                            logger.warn("Field {} type changed from {} to {}", fieldName, existingType, type);
                            // TODO: handle type conflicts (e.g. use the more general type)
                        }
                        detectedFields.put(fieldName, type);
                    }
                }
            } catch (JsonProcessingException e) {
                logger.error("Error processing event JSON: {}", e.getMessage());
            }
        }

        try {
            writeApplicationPackage();
        } catch (IOException e) {
            logger.error("Error writing application package: {}", e.getMessage());
            return;
        }
    }

    private void writeServicesXml() throws IOException {
        String servicesXml = null;
        try {
            servicesXml = readTemplate("services.xml");
            // replace document_type in services.xml with the actual document type
            // TODO: handle dynamic document type
            servicesXml = servicesXml.replace("document_type", config.getDocumentType());
        } catch (IOException e) {
            logger.error("Error reading services.xml: {}", e.getMessage());
            throw e;
        }

        try {
            Path filePath = Paths.get(config.getApplicationPackageDir(), "services.xml");
            Files.write(filePath, servicesXml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error writing services.xml: {}", e.getMessage());
            throw e;
        }
    }

    // TODO: handle multiple/new schemas
    private void writeSchemas() throws IOException {
        String schema = null;
        // TODO: use OS-independent path
        String schemaPath = "schemas/document_type.sd";
        try {
            schema = readTemplate(schemaPath);
            // replace document_type in schema with the actual document type
            schema = schema.replace("document_type", config.getDocumentType());
            
            // Build fields from all detected types
            StringBuilder fieldsBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : detectedFields.entrySet()) {
                String fieldName = entry.getKey();
                String fieldType = entry.getValue();
                
                String fieldDefinition = config.getFieldTypeMapping().getOrDefault(
                    fieldType, defaultTypeMapping.get(fieldType));
                
                if (fieldDefinition != null) {
                    fieldsBuilder.append("        field ")
                               .append(fieldName)
                               .append(" ")
                               .append(fieldDefinition)
                               .append("\n\n");
                } else {
                    logger.warn("No mapping found for field {} of type {}", fieldName, fieldType);
                }
            }
            
            // Replace placeholder with generated fields
            String generatedFields = fieldsBuilder.toString();
            //strip the last two newlines
            generatedFields = generatedFields.substring(0, generatedFields.length() - 2);
            schema = schema.replace(
                "        # Fields will be added here",
                generatedFields
            );

            // remove the comment from the first line
            schema = schema.replace("# This is a template that will be filled in with the actual schema", "");
        } catch (IOException e) {
            logger.error("Error reading {}: {}", schemaPath, e.getMessage());
            throw e;
        }

        String targetSchemaPath = "schemas/" + config.getDocumentType() + ".sd";
        try {
            Path filePath = Paths.get(config.getApplicationPackageDir(), targetSchemaPath);
            Files.write(filePath, schema.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error writing {}: {}", schemaPath, e.getMessage());
            throw e;
        }
    }

    private String detectType(Object value) {
        if (value instanceof String) {
            return "string";
        } else if (value instanceof Integer || value instanceof Long) {
            return "long";
        }
        // For now we only handle string and int
        logger.warn("Unsupported type: {}", value.getClass().getName());
        return null;
    }

    private String readTemplate(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(
                "application_package_template/" + path)) {
            if (is == null) {
                throw new IOException("Could not find template: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    private void writeApplicationPackage() throws IOException {
        // one application package update at a time => write.lock
        Path writeLockFile = Paths.get(config.getApplicationPackageDir(), "write.lock");
        long remainingAttempts = config.getMaxRetries();

        logger.info("Writing application package to {}", config.getApplicationPackageDir());
        
        while (remainingAttempts > 0) {
            try {
                Files.createFile(writeLockFile);
                break; // Success - exit the retry loop
            } catch (FileAlreadyExistsException e) {
                remainingAttempts--;
                if (remainingAttempts == 0) {
                    throw new LockException("Write lock file exists after " + config.getMaxRetries() + " attempts.");
                }
                logger.debug("Lock exists, waiting {}s before retry {}/{}", 
                    config.getGracePeriod(), config.getMaxRetries() - remainingAttempts, config.getMaxRetries());
                try {
                    Thread.sleep(config.getGracePeriod() * 1000);
                } catch (InterruptedException ie) {
                    throw new LockException("Interrupted while waiting for lock");
                }
            }
        }

        try {
            writeServicesXml();
            writeSchemas();
            logger.info("Wrote application package", config.getApplicationPackageDir());
        } finally {
            Files.delete(writeLockFile);
        }
    }
} 