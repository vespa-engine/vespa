package org.logstashplugins;

import java.util.Map;

public class DryRunConfig {
    private final boolean deployPackage;
    private final String configServer;
    private final String documentType;
    private final Map<String, String> conflictResolution;
    private final String applicationPackageDir;
    private final long idleBatches;
    private final long maxRetries;
    private final long gracePeriod;
    private final String typeMappingsFile;

    public DryRunConfig(boolean deployPackage, 
                       String configServer, 
                       String documentType,
                       long idleBatches,
                       String applicationPackageDir,
                       String typeMappingsFile,
                       Map<String, String> conflictResolution,
                       long maxRetries,
                       long gracePeriod) {
        this.deployPackage = deployPackage;
        this.configServer = configServer;
        this.documentType = documentType;
        this.conflictResolution = conflictResolution;
        this.applicationPackageDir = applicationPackageDir;
        this.idleBatches = idleBatches;
        this.maxRetries = maxRetries;
        this.gracePeriod = gracePeriod;
        this.typeMappingsFile = typeMappingsFile;
    }

    public boolean isDeployPackage() { return deployPackage; }
    public String getConfigServer() { return configServer; }
    public String getDocumentType() { return documentType; }
    public String getApplicationPackageDir() { return applicationPackageDir; }
    public Map<String, String> getConflictResolution() { return conflictResolution; }
    public long getIdleBatches() { return idleBatches; }
    public long getMaxRetries() { return maxRetries; }
    public long getGracePeriod() { return gracePeriod; }
    public String getTypeMappingsFile() { return typeMappingsFile; }
} 