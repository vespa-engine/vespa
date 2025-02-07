package org.logstashplugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import co.elastic.logstash.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.logstash.ObjectMappers;

public class VespaDryRunner {
    private static final Logger logger = LogManager.getLogger(VespaDryRunner.class);
    private final DryRunConfig config;
    private final Map<String, String> defaultTypeMapping;
    private final Map<String, String> detectedFields = new HashMap<>();
    private final ObjectMapper objectMapper;

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
    }

    public void run(Collection<Event> events) {
        // the batch may be empty => nothing to do
        if (events.isEmpty()) {
            return;
        }

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
            writeServicesXml();
        } catch (IOException e) {
            logger.error("Error reading&writing services.xml (skipping this batch): {}", e.getMessage());
            return;
        }

        try {
            writeSchemas();
        } catch (IOException e) {
            logger.error("Error reading&writing schemas (skipping this batch): {}", e.getMessage());
            return;
        }

        // TODO: only write this if we changed something with this batch
        logger.info("Wrote application package to {}", config.getApplicationPackageDir());
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
} 