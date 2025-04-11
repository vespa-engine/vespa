// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    private final QuickStartConfig config;
    private static final Logger logger = LogManager.getLogger(VespaAppPackageWriter.class);
    private final Map<String, List<String>> typeMappings;

    public VespaAppPackageWriter(QuickStartConfig config, Map<String, List<String>> typeMappings) {
        this.config = config;
        this.typeMappings = typeMappings;

        // create the application package directory, if it doesn't exist. Also write the "schemas" directory within it
        Path schemasDir = Paths.get(config.getApplicationPackageDir(), "schemas");
        if (!Files.exists(schemasDir)) {
            try {
                Files.createDirectories(schemasDir);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error creating application package + schemas directory: " + e.getMessage());
            }
        }

        if (config.isGenerateMtlsCertificates()) {
            try {
                generateMtlsCertificates();
            } catch (Exception e) {
                throw new IllegalArgumentException("Error generating mTLS certificates: " + e.getMessage());
            }
        }

        // Copy the .vespaignore file from resources if it doesn't exist in the target directory
        try {
            Path vespaIgnorePath = Paths.get(config.getApplicationPackageDir(), ".vespaignore");
            if (!Files.exists(vespaIgnorePath)) {
                logger.info("Copying .vespaignore file from the template application package");
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(
                         Paths.get("application_package_template", ".vespaignore").toString())) {
                    if (is == null) {
                        logger.error("Could not find .vespaignore template in resources");
                    } else {
                        Files.copy(is, vespaIgnorePath);
                        logger.info("Created .vespaignore file at: {}", vespaIgnorePath);
                    }
                }
            } else {
                logger.info(".vespaignore file already exists in the target directory, not overwriting");
            }
        } catch (IOException e) {
            logger.error("Error creating .vespaignore file: {}", e.getMessage());
        }
    }

    private void generateMtlsCertificates() throws Exception {
        String clientCertPath = config.getClientCert();
        String clientKeyPath = config.getClientKey();
        
        // this shouldn't happen, but better safe than NPE
        if (clientCertPath == null || clientKeyPath == null) {
            logger.error("Client certificate or key path is null. Cannot generate mTLS certificates.");
            throw new IllegalArgumentException("Client certificate or key path is null");
        }
        
        // make sure the parent directories of these certificates exists. Otherwise, generate them
        Path clientCertDir = Paths.get(clientCertPath).getParent();
        Path clientKeyDir = Paths.get(clientKeyPath).getParent();
        
        if (clientCertDir == null || clientKeyDir == null) {
            logger.error("Invalid certificate or key path. Cannot determine parent directory.");
            throw new IllegalArgumentException("Invalid certificate or key path");
        }
        
        if (!Files.exists(clientCertDir)) {
            logger.info("Creating directory for clients certificate: {}", clientCertDir);
            Files.createDirectories(clientCertDir);
        }
        
        if (!Files.exists(clientKeyDir)) {
            logger.info("Creating directory for data plane private key: {}", clientKeyDir);
            Files.createDirectories(clientKeyDir);
        }

        logger.info("Generating mTLS certificates:");
        logger.info("Clients certificate path: {}", clientCertPath);
        logger.info("Data plane private key path: {}", clientKeyPath);
        
        String cn = config.getCertificateCommonName();
        int validityDays = config.getCertificateValidityDays();
        SelfSignedCertGenerator.generate(
            cn,
            validityDays,
            clientKeyPath,
            clientCertPath
        );
        
        logger.info("Successfully generated mTLS certificates");
        
        if (config.isVespaCloud()) {
            // Copy certificates to the user's home directory for Vespa CLI
            copyVespaCloudCertificatesToUserHome();
            
            logger.info("For Vespa Cloud deployment, these certificates will be used for authentication");
        }
    }
    
    private void copyVespaCloudCertificatesToUserHome() {
        if (!config.isVespaCloud()) {
            return;
        }
        
        try {
            String tenant = config.getVespaCloudTenant();
            String application = config.getVespaCloudApplication();
            String instance = config.getVespaCloudInstance();
            
            // Get user home directory
            String userHome = System.getProperty("user.home");
            if (userHome == null) {
                logger.warn("Cannot determine user home directory, skipping certificate copy");
                return;
            }
            
            // Create target directory path
            Path vespaAppDir = Paths.get(userHome, ".vespa", tenant + "." + application + "." + instance);
            if (!Files.exists(vespaAppDir)) {
                logger.info("Creating Vespa CLI app directory: {}", vespaAppDir);
                Files.createDirectories(vespaAppDir);
            }
            
            // Define source and target paths
            Path sourceCertPath = Paths.get(config.getClientCert());
            Path sourceKeyPath = Paths.get(config.getClientKey());
            Path targetCertPath = vespaAppDir.resolve("data-plane-public-cert.pem");
            Path targetKeyPath = vespaAppDir.resolve("data-plane-private-key.pem");
            
            // Bail out if the files already exist
            if (Files.exists(targetCertPath) || Files.exists(targetKeyPath)) {
                logger.warn("Certificate or key already exists in Vespa CLI directory, not overwriting: {}", targetCertPath);
                return;
            }
            
            // Copy files
            Files.copy(sourceCertPath, targetCertPath);
            Files.copy(sourceKeyPath, targetKeyPath);
            
            // Set permissions on the files to be readable only by the owner
            targetCertPath.toFile().setReadable(false, false);
            targetCertPath.toFile().setReadable(true, true);
            targetCertPath.toFile().setWritable(false, false);
            targetCertPath.toFile().setWritable(true, true);
            
            targetKeyPath.toFile().setReadable(false, false);
            targetKeyPath.toFile().setReadable(true, true);
            targetKeyPath.toFile().setWritable(false, false);
            targetKeyPath.toFile().setWritable(true, true);
            
            logger.info("Certificates copied to Vespa CLI directory: {}", vespaAppDir);
            logger.info("These can be used with Vespa CLI when you do 'vespa deploy'");
            
        } catch (Exception e) {
            logger.warn("Failed to copy certificates to user home directory: {}", e.getMessage());
            logger.info("To use these certificates with Vespa CLI, copy them manually:");
            logger.info("  mkdir -p ~/.vespa/{}.{}.{}", 
                config.getVespaCloudTenant(), config.getVespaCloudApplication(), config.getVespaCloudInstance());
            logger.info("  cp {} ~/.vespa/{}.{}.{}/data-plane-public-cert.pem", 
                config.getClientCert(), config.getVespaCloudTenant(), config.getVespaCloudApplication(), config.getVespaCloudInstance());
            logger.info("  cp {} ~/.vespa/{}.{}.{}/", 
                config.getClientKey(), config.getVespaCloudTenant(), config.getVespaCloudApplication(), config.getVespaCloudInstance());
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
            writeSchema(detectedFields);

            logger.info("Wrote application package to {}", config.getApplicationPackageDir());
        } finally {
            Files.deleteIfExists(writeLockFile);
        }
    }

    /**
     * Reconciles the detected fields with the existing fields.
     * 
     * @param detectedFields The fields detected in the input data
     * @param existingFields The fields already defined in the application package
     * @return A map of reconciled fields
     */
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
                    logger.info("Resolving type conflict for field {} ({}) vs ({})", 
                                fieldName, detectedFieldType, existingFieldType);
                    String resolvedType = resolveTypeConflict(detectedFieldType, existingFieldType, typeConflictResolution);
                    if (resolvedType == null) {
                        logger.error("Cannot resolve type conflict for field {} ({}) vs ({})", 
                                        fieldName, detectedFieldType, existingFieldType);
                        logger.warn("Leaving field {} as is", fieldName);
                        result.put(fieldName, existingFieldType);
                    } else {
                        logger.info("Resolved type conflict for field {} ({}) vs ({}) to {}", 
                                    fieldName, detectedFieldType, existingFieldType, resolvedType);
                        result.put(fieldName, resolvedType);
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Resolves a type conflict between two Vespa field types.
     * Uses the type conflict resolution rules defined in the type_conflict_resolution.yml file.
     * If no resolution is found, returns null.
     *
     * @param type1 First Vespa field type
     * @param type2 Second Vespa field type
     * @return The resolved type, or null if no resolution is found
     */
    String resolveTypeConflict(String type1, String type2) {
        Map<String, Map<String, String>> typeConflictResolution = loadTypeConflictResolution();
        return resolveTypeConflict(type1, type2, typeConflictResolution);
    }

    String resolveTypeConflict(String type1, String type2, Map<String, Map<String, String>> resolutions) {
        // Check if we have an array in either type
        Pattern arrayPattern = Pattern.compile("array<([^>]+)>\\[(\\d+)\\]");
        Matcher matcher1 = arrayPattern.matcher(type1);
        Matcher matcher2 = arrayPattern.matcher(type2);

        Integer type1arraySize = null;
        Integer type2arraySize = null;
        String type1baseType = null;
        String type2baseType = null;
        if (matcher1.matches()) {
            type1arraySize = Integer.parseInt(matcher1.group(2));
            type1baseType = matcher1.group(1);
        }
        if (matcher2.matches()) {
            type2arraySize = Integer.parseInt(matcher2.group(2));
            type2baseType = matcher2.group(1);
        }
        
        // two arrays?
        if (type1arraySize != null && type2arraySize != null) {
            // If base types are the same but array sizes differ, use variablearray
            if (type1baseType.equals(type2baseType)) {
                return "variablearray<" + type1baseType + ">";
            } else {
                // see if we can resolve the base types
                String resolvedBaseType = getBaseResolvedType(type1baseType, type2baseType, resolutions);
                // if we can, return a variablearray with the resolved base type
                if (resolvedBaseType != null) {
                    return "variablearray<" + resolvedBaseType + ">";
                } else {
                    // otherwise, we can't resolve the conflict
                    return null;
                }
            }
        }

        // one array and one non-array?
        // look up the resolutions without the array size
        if (type1arraySize != null) {
            String resolvedBaseType = getBaseResolvedType("array<" + type1baseType + ">", type2, resolutions);
            // if the result is an array, return it with the array size
            if (resolvedBaseType != null && resolvedBaseType.startsWith("array<")) {
                return resolvedBaseType + "[" + type1arraySize + "]";
            } else {
                // otherwise, we return the resolved type as is
                return resolvedBaseType;
            }
        }
        // same logic as above
        if (type2arraySize != null) {
            String resolvedBaseType = getBaseResolvedType("array<" + type2baseType + ">", type1, resolutions);
            if (resolvedBaseType != null && resolvedBaseType.startsWith("array<")) {
                return resolvedBaseType + "[" + type2arraySize + "]";
            } else {
                return resolvedBaseType;
            }
        }

        // two non-array types?
        // just look them up in the resolutions
        return getBaseResolvedType(type1, type2, resolutions);
    }

    private String getBaseResolvedType(String type1, String type2, Map<String, Map<String, String>> resolutions) {
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

    Map<String, Map<String, String>> loadTypeConflictResolution() {
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

    /**
     * Writes the services.xml file by replacing template placeholders with actual values.
     * 
     * @throws IOException if an error occurs while writing the services.xml file
     */
    private void writeServicesXml() throws IOException {
        String servicesXml = readTemplate("services.xml");
        
        // replace document_type in services.xml with the actual document type
        servicesXml = servicesXml.replace("document_type", config.getDocumentType());

        // If mTLS is enabled, uncomment the mTLS configuration
        if (config.isGenerateMtlsCertificates()) {
            servicesXml = servicesXml
                .replace("<!-- MTLS", "")  // remove opening comment
                .replace("END MTLS -->", "");  // remove closing comment
        }

        try {
            Path filePath = Paths.get(config.getApplicationPackageDir(), "services.xml");
            Files.write(filePath, servicesXml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error writing services.xml: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Reads a template file from the "resources/application_package_template" directory.
     * 
     * @param path The path to the template file
     * @return The content of the template file
     * @throws IOException if an error occurs while reading the template file
     */
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

    /**
     * Writes the schema file by replacing template placeholders with definitions of detected fields.
     * 
     * @param detectedFields The fields detected in the input data
     * @throws IOException if an error occurs while writing the schema file
     */
    private void writeSchema(Map<String, String> detectedFields) throws IOException {
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
