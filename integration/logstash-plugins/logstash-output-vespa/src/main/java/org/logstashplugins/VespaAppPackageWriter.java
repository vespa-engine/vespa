package org.logstashplugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.logstash.LockException;
import org.yaml.snakeyaml.Yaml;

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

        // do we already have a schema for this document type?
        // if so, we need to merge the new fields with the existing ones
        try {
            Map<String, String> existingFields = readExistingFields();

            for (Map.Entry<String, String> entry : existingFields.entrySet()) {
                logger.debug("Found existing field: {} {}", entry.getKey(), entry.getValue());
            }

            detectedFields = reconcileFields(detectedFields, existingFields);
        } catch (IOException e) {
            logger.info("No schema found for document type {}, creating new one",
                        config.getDocumentType());
        }

        try {
            writeServicesXml();
            writeSchemas(detectedFields);
            logger.info("Wrote application package", config.getApplicationPackageDir());
        } finally {
            Files.delete(writeLockFile);
        }
    }

    private Map<String, String> reconcileFields(Map<String, String> detectedFields, Map<String, String> existingFields) {
        Map<String, String> result = new HashMap<>();
        Map<String, Map<String, String>> typeConflictResolution = loadTypeConflictResolution();
        
        // First add all fields that only exist in one of the maps
        for (Map.Entry<String, String> entry : detectedFields.entrySet()) {
            String fieldName = entry.getKey();
            if (!existingFields.containsKey(fieldName)) {
                result.put(fieldName, entry.getValue());
            }
        }
        
        for (Map.Entry<String, String> entry : existingFields.entrySet()) {
            String fieldName = entry.getKey();
            if (!detectedFields.containsKey(fieldName)) {
                result.put(fieldName, entry.getValue());
            }
        }
        
        // Now handle conflicts
        for (String fieldName : detectedFields.keySet()) {
            if (existingFields.containsKey(fieldName)) {
                String detectedFieldType = detectedFields.get(fieldName);
                String existingFieldType = existingFields.get(fieldName);
                
                if (detectedFieldType.equals(existingFieldType)) {
                    result.put(fieldName, detectedFieldType);
                } else {
                    String resolvedType = resolveTypeConflict(detectedFieldType, existingFieldType, typeConflictResolution);
                    if (resolvedType == null) {
                        logger.error("Cannot resolve type conflict for field {} ({}) vs ({})", 
                                        fieldName, detectedFieldType, existingFieldType);
                        logger.warn("Leaving field {} as is", fieldName);
                        result.put(fieldName, existingFieldType);
                    } else {
                        result.put(fieldName, resolvedType);
                    }
                }
            }
        }
        
        return result;
    }

    private String resolveTypeConflict(String type1, String type2, Map<String, Map<String, String>> resolutions) {
        // Try both orderings since the resolution might only be defined one way
        Map<String, String> type1Resolutions = resolutions.get(type1);
        if (type1Resolutions != null && type1Resolutions.containsKey(type2)) {
            return type1Resolutions.get(type2);
        }
        
        Map<String, String> type2Resolutions = resolutions.get(type2);
        if (type2Resolutions != null && type2Resolutions.containsKey(type1)) {
            return type2Resolutions.get(type1);
        }
        
        return null;
    }

    private Map<String, Map<String, String>> loadTypeConflictResolution() {
        String source = "built-in type conflict resolution";
        InputStream resolutionStream = getClass().getClassLoader().getResourceAsStream("type_conflict_resolution.yml");

        if (config.getTypeConflictResolutionFile() != null) {
            try {
                resolutionStream = Files.newInputStream(Paths.get(config.getTypeConflictResolutionFile()));
                source = config.getTypeConflictResolutionFile();
            } catch (IOException e) {
                logger.warn("Could not load custom type conflict resolution file: {}. Using built-in defaults.", e.getMessage());
            }
        }

        logger.info("Loading type conflict resolution from {}", source);
        try (InputStream is = resolutionStream) {
            Yaml yaml = new Yaml();
            return yaml.load(is);
        } catch (IOException e) {
            logger.error("Error loading type conflict resolution: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, String> readExistingFields() throws IOException {
        Path schemaPath = Paths.get(config.getApplicationPackageDir(),
                            "schemas", config.getDocumentType() + ".sd");
        String schema = Files.readString(schemaPath, StandardCharsets.UTF_8);
        Map<String, String> existingFields = new HashMap<>();
        
        // read from annotation lines like
        //       # price: long
        Pattern annotationPattern = Pattern.compile("       # (\\w+): (.+)");
        Matcher matcher = annotationPattern.matcher(schema);
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String fieldType = matcher.group(2);
            existingFields.put(fieldName, fieldType);
        }
        
        return existingFields;
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

    /* 
     * Replace the {{FIELD_NAME}} and {{ARRAY_SIZE}} placeholders in the template read from the file
     * with the actual field name and array size.
     */
    private String processTemplate(String template, String fieldName, String fieldType,
                                  String arraySize, boolean isDocumentField) {
        String result = template.replace("{{FIELD_NAME}}", fieldName);
        if (arraySize != null) {
            result = result.replace("{{ARRAY_SIZE}}", arraySize);
        }
        if (isDocumentField) {
            // To make reading easier, let's pre-pend the field definition
            // with the field name and type
            String annotation = "       # " + fieldName + ": " + fieldType;
            if (arraySize != null) {
                annotation += "[" + arraySize + "]";
            }
            result = annotation + "\n" + result;
        }
        return result;
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
            String arraySize = null;

            if (fieldType.contains("[")) {
                // array field, we need to extract the size
                String[] parts = fieldType.split("\\[");
                arraySize = parts[1].replace("]", "");
                fieldType = parts[0];
            }
            
            List<String> fieldTemplates = typeMappings.get(fieldType);
            if (fieldTemplates != null && !fieldTemplates.isEmpty()) {
                // First template goes into the document block
                documentFieldsBuilder.append(processTemplate(fieldTemplates.get(0),
                                            fieldName, fieldType, arraySize, true));
                
                // Additional templates are synthetic fields
                for (int i = 1; i < fieldTemplates.size(); i++) {
                    syntheticFieldsBuilder.append(processTemplate(fieldTemplates.get(i),
                                            fieldName, fieldType, arraySize, false));
                }
            } else {
                logger.warn("No mapping found for field {} of type {}", fieldName, fieldType);
            }
        }
        
        // Replace placeholders with generated fields, removing the last newline
        String documentFields = removeTrailingNewline(documentFieldsBuilder.toString());
        String syntheticFields = removeTrailingNewline(syntheticFieldsBuilder.toString());
        
        schema = schema.replace("        # Fields will be added here", documentFields)
                      .replace("    # synthetic fields can be added here", syntheticFields);

        writeSchemaFile(schema, config.getDocumentType());
    }

    private String removeTrailingNewline(String str) {
        if (str.endsWith("\n")) {
            return str.substring(0, str.length() - 1);
        }
        return str;
    }

    private void writeSchemaFile(String schema, String documentType) throws IOException {
        // remove the comment from the first line
        schema = schema.replace("# This is a template that will be filled in with the actual schema", "");
        
        Path targetSchemaPath = Paths.get("schemas", documentType + ".sd");
        Path filePath = Paths.get(config.getApplicationPackageDir()).resolve(targetSchemaPath);
        Files.write(filePath, schema.getBytes(StandardCharsets.UTF_8));
    }
}
