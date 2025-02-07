package org.logstashplugins;

import java.util.Map;

public class DryRunConfig {
    private final boolean deployPackage;
    private final String configServer;
    private final String documentType;
    private final Map<String, String> fieldTypeMapping;
    private final Map<String, String> conflictResolution;
    private final String applicationPackageDir;

    public DryRunConfig(boolean deployPackage, 
                       String configServer, 
                       String documentType,
                       String applicationPackageDir,
                       Map<String, String> fieldTypeMapping,
                       Map<String, String> conflictResolution) {
        this.deployPackage = deployPackage;
        this.configServer = configServer;
        this.documentType = documentType;
        this.fieldTypeMapping = fieldTypeMapping;
        this.conflictResolution = conflictResolution;
        this.applicationPackageDir = applicationPackageDir;
    }

    public boolean isDeployPackage() { return deployPackage; }
    public String getConfigServer() { return configServer; }
    public String getDocumentType() { return documentType; }
    public String getApplicationPackageDir() { return applicationPackageDir; }
    public Map<String, String> getFieldTypeMapping() { return fieldTypeMapping; }
    public Map<String, String> getConflictResolution() { return conflictResolution; }
} 