// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package org.logstashplugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import co.elastic.logstash.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import org.logstash.ObjectMappers;
import org.yaml.snakeyaml.Yaml;

public class VespaQuickStarter {
    private static final Logger logger = LogManager.getLogger(VespaQuickStarter.class);
    private final QuickStartConfig config;
    private final Map<String, List<String>> typeMappings;
    private final Map<String, String> detectedFields = new HashMap<>();
    private final ObjectMapper objectMapper;
    private int emptyBatchesCount = 0;
    private boolean gotEvents = false;
    final VespaDeployer deployer;
    private final VespaAppPackageWriter appPackageWriter;

    public VespaQuickStarter(QuickStartConfig config) {
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
    
    // Getter for emptyBatchesCount - useful for testing
    public int getEmptyBatchesCount() {
        return emptyBatchesCount;
    }
    
    // Getter for gotEvents - useful for testing
    public boolean hasGotEvents() {
        return gotEvents;
    }

    public void run(Collection<Event> events) {
        // the batch may be empty - a hiccup or we're done processing?
        if (events.isEmpty()) {
            emptyBatchesCount++;
            logger.debug("Empty batch {}, got events in this thread: {}", emptyBatchesCount, gotEvents);
            if (config.isDeployPackage() && emptyBatchesCount >= config.getIdleBatches() && gotEvents) {
                logger.info("{} batches of {} empty, deploying application package", emptyBatchesCount, config.getIdleBatches());
                deployer.deployApplicationPackage();
                emptyBatchesCount = 0;
            }
            return;
        }
        // Got events - reset counters
        emptyBatchesCount = 0;
        gotEvents = true;

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
                            logger.warn("Field '{}' type changed from {} to {}", fieldName, existingType, type);
                            String resolvedType = appPackageWriter.resolveTypeConflict(existingType, type);
                            if (resolvedType != null) {
                                logger.info("Resolved type conflict for field '{}': using type {}", fieldName, resolvedType);
                                type = resolvedType;
                            } else {
                                logger.warn("Could not resolve type conflict for field '{}', simply switching to type '{}'. " +
                                            "This implies that previous documents might throw errors when indexed.", fieldName, type);
                            }
                        }

                        if (existingType == null) {
                            logger.info("Detected new field '{}' type: {}", fieldName, type);
                        }
                        detectedFields.put(fieldName, type);
                    }
                }
            } catch (JsonProcessingException e) {
                logger.error("Error processing event JSON: {} {}", e.getMessage(), e.getStackTrace());
            }
        }

        try {
            if (detectedFields.isEmpty()) {
                logger.warn("No fields detected, skipping application package write");
            } else {
                appPackageWriter.writeApplicationPackage(detectedFields);
            }
        } catch (IOException e) {
            logger.error("Error writing application package: {} {}", e.getMessage(), e.getStackTrace());
        }
    }

    String detectType(Object value) {
        if (value instanceof String) {
            return "string";
        } else if (value instanceof Integer) {
            // we really care if this is an int8 (byte) so that it fits in a tensor
            // otherwise we can just assume it's a long
            int intValue = (Integer) value;
            if (intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE) {
                return "int8";
            }
            return "long";
        } else if (value instanceof Long) {
            return "long";
        } else if (value instanceof Double) {
            // Jackson always returns double for float values,
            // but most of them are actually floats
            return "float";
        } else if (value instanceof Boolean) {
            return "bool";
        } else if (value instanceof Map) {
            // lat + lng => position type
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.containsKey("lat") && map.containsKey("lng")) {
                return "position";
            }

            // infer the type of the map from the first value
            try {
                Object firstValue = map.values().iterator().next();
                String firstValueType = detectType(firstValue);
                if (firstValueType != null) {
                    return "object<" + firstValueType + ">";
                }
            } catch (NoSuchElementException e) {
                logger.error("Empty map: {}", value.getClass().getName());
                return null;
            }
            logger.error("Unsupported map type: {}", value.getClass().getName());
            return null;
        } else if (value instanceof List) {
            // infer the type of the list from the first element
            List<?> list = (List<?>) value;
            if (!list.isEmpty()) {
                Object firstElement = list.get(0);
                String elementType = detectType(firstElement);
                if (elementType != null) {
                    return "array<" + elementType + ">[" + list.size() + "]";
                }
            }
            logger.error("Empty array or array with unsupported element type for field");
            return null;
        }
        
        logger.error("Unsupported type: {}", value.getClass().getName());
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