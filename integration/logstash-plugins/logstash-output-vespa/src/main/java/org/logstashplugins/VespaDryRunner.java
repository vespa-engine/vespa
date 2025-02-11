package org.logstashplugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import co.elastic.logstash.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import org.logstash.ObjectMappers;
import org.yaml.snakeyaml.Yaml;

public class VespaDryRunner {
    private static final Logger logger = LogManager.getLogger(VespaDryRunner.class);
    private final DryRunConfig config;
    private final Map<String, List<String>> typeMappings;
    private final Map<String, String> detectedFields = new HashMap<>();
    private final ObjectMapper objectMapper;
    private int emptyBatchesCount = 0;
    final VespaDeployer deployer;
    private final VespaAppPackageWriter appPackageWriter;

    public VespaDryRunner(DryRunConfig config) {
        this.config = config;
        this.objectMapper = ObjectMappers.JSON_MAPPER;
        try {
            this.typeMappings = loadTypeMappings();
        } catch (IOException e) {
            logger.error("Error loading type mappings: {}", e.getMessage());
            throw new IllegalStateException("Could not load type mappings", e);
        }

        this.deployer = new VespaDeployer(config);
        this.appPackageWriter = new VespaAppPackageWriter(config, typeMappings);
    }

    public void run(Collection<Event> events) {
        // the batch may be empty - a hiccup or we're done processing?
        if (events.isEmpty()) {
            emptyBatchesCount++;
            if (config.isDeployPackage() && emptyBatchesCount >= config.getIdleBatches()) {
                deployer.deployApplicationPackage();
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
            appPackageWriter.writeApplicationPackage(detectedFields);
        } catch (IOException e) {
            logger.error("Error writing application package: {}", e.getMessage());
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

    private Map<String, List<String>> loadTypeMappings() throws IOException {
        String source = "built-in mappings";
        InputStream mappingStream;

        if (config.getTypeMappingsFile() != null) {
            try {
                mappingStream = Files.newInputStream(Paths.get(config.getTypeMappingsFile()));
                source = config.getTypeMappingsFile();
            } catch (IOException e) {
                logger.warn("Could not load custom type mappings file: {}. Using built-in defaults.", e.getMessage());
                mappingStream = getClass().getClassLoader().getResourceAsStream("type_mappings.yml");
            }
        } else {
            mappingStream = getClass().getClassLoader().getResourceAsStream("type_mappings.yml");
        }

        if (mappingStream == null) {
            throw new IOException("Could not find type mappings");
        }

        logger.info("Loading type mappings from {}", source);
        try (InputStream is = mappingStream) {
            Yaml yaml = new Yaml();
            Map<String, List<String>> typeConfigs = yaml.load(is);
            
            Map<String, List<String>> mappings = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : typeConfigs.entrySet()) {
                String type = entry.getKey();
                List<String> fieldTemplates = entry.getValue();
                mappings.put(type, fieldTemplates);
            }
            
            return mappings;
        }
    }
} 