package org.logstashplugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.logstash.LockException;

public class VespaAppPackageWriter {
    private final DryRunConfig config;
    private static final Logger logger = LogManager.getLogger(VespaAppPackageWriter.class);
    private final Map<String, List<String>> typeMappings;

    public VespaAppPackageWriter(DryRunConfig config, Map<String, List<String>> typeMappings) {
        this.config = config;
        this.typeMappings = typeMappings;

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

    void writeApplicationPackage(Map<String, String> detectedFields) throws IOException {
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
            writeSchemas(detectedFields);
            logger.info("Wrote application package", config.getApplicationPackageDir());
        } finally {
            Files.delete(writeLockFile);
        }
    }

    private void writeServicesXml() throws IOException {
        String servicesXml = null;
        servicesXml = readTemplate("services.xml");
        // replace document_type in services.xml with the actual document type
        // TODO: handle dynamic document type
        servicesXml = servicesXml.replace("document_type", config.getDocumentType());

        try {
            Path filePath = Paths.get(config.getApplicationPackageDir(), "services.xml");
            Files.write(filePath, servicesXml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error writing services.xml: {}", e.getMessage());
            throw e;
        }
    }

    private String readTemplate(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(
                Paths.get("application_package_template", path).toString())) {
            if (is == null) {
                throw new IOException("Could not find template: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Error reading template: {}", e.getMessage());
            throw e;
        }
    }

    // TODO: handle multiple/new schemas
    private void writeSchemas(Map<String, String> detectedFields) throws IOException {
        Path schemaPath = Paths.get("schemas", "document_type.sd");
        String schema = readTemplate(schemaPath.toString());
        // replace document_type in schema with the actual document type
        schema = schema.replace("document_type", config.getDocumentType());
        
        // This is where we accumulate field definitions for both "main" and "synthetic" fields
        StringBuilder documentFieldsBuilder = new StringBuilder();
        StringBuilder syntheticFieldsBuilder = new StringBuilder();
        
        for (Map.Entry<String, String> entry : detectedFields.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            
            List<String> fieldTemplates = typeMappings.get(fieldType);
            if (fieldTemplates != null && !fieldTemplates.isEmpty()) {
                // First template goes into the document block
                String documentField = fieldTemplates.get(0).replace("{{FIELD_NAME}}", fieldName);
                documentFieldsBuilder.append(documentField);
                
                // Additional templates are synthetic fields
                for (int i = 1; i < fieldTemplates.size(); i++) {
                    String syntheticField = fieldTemplates.get(i).replace("{{FIELD_NAME}}", fieldName);
                    syntheticFieldsBuilder.append(syntheticField);
                }
            } else {
                logger.warn("No mapping found for field {} of type {}", fieldName, fieldType);
            }
        }
        
        // Replace placeholders with generated fields, removing the last newline
        String documentFields = documentFieldsBuilder.toString().substring(0, documentFieldsBuilder.length() - 1);
        String syntheticFields = syntheticFieldsBuilder.toString().substring(0, syntheticFieldsBuilder.length() - 1);
        
        schema = schema.replace("        # Fields will be added here", documentFields)
                      .replace("    # synthetic fields can be added here", syntheticFields);

        writeSchemaFile(schema, config.getDocumentType());
    }

    private void writeSchemaFile(String schema, String documentType) throws IOException {
        // remove the comment from the first line
        schema = schema.replace("# This is a template that will be filled in with the actual schema", "");
        
        Path targetSchemaPath = Paths.get("schemas", documentType + ".sd");
        Path filePath = Paths.get(config.getApplicationPackageDir()).resolve(targetSchemaPath);
        Files.write(filePath, schema.getBytes(StandardCharsets.UTF_8));
    }
}
